(() => {
    "use strict";

    // ---------- State ----------
    let documents = [];
    let activeDocId = null;     // null = search across all
    let busy = false;

    // ---------- Elements ----------
    const $ = (sel) => document.querySelector(sel);

    const dropZone    = $("#dropZone");
    const fileInput   = $("#fileInput");
    const uploadSt    = $("#uploadStatus");
    const docListEl   = $("#docList");
    const refreshBtn  = $("#refreshDocs");
    const scopeLabel  = $("#scopeLabel");
    const messagesEl  = $("#messages");
    const chatForm    = $("#chatForm");
    const questionEl  = $("#question");
    const sendBtn     = $("#sendBtn");
    const clearBtn    = $("#clearChat");

    // =========================================================
    // Upload
    // =========================================================
    dropZone.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", (e) => {
        if (e.target.files.length) uploadFile(e.target.files[0]);
    });

    ["dragenter", "dragover"].forEach((ev) =>
        dropZone.addEventListener(ev, (e) => {
            e.preventDefault();
            dropZone.classList.add("dragover");
        })
    );
    ["dragleave", "drop"].forEach((ev) =>
        dropZone.addEventListener(ev, (e) => {
            e.preventDefault();
            dropZone.classList.remove("dragover");
        })
    );
    dropZone.addEventListener("drop", (e) => {
        const f = e.dataTransfer.files?.[0];
        if (f) uploadFile(f);
    });

    async function uploadFile(file) {
        if (!file.name.toLowerCase().endsWith(".pdf")) {
            showUploadStatus("error", "Only PDF files are supported.");
            return;
        }
        showUploadStatus("info", `Uploading ${file.name}…`);

        const form = new FormData();
        form.append("file", file);

        try {
            const res = await fetch("/api/documents/upload", {
                method: "POST",
                body: form,
            });
            if (!res.ok) throw new Error(await readError(res));
            const data = await res.json();
            showUploadStatus(
                "success",
                `Indexed "${data.fileName}" — ${data.chunkCount} chunks`
            );
            await loadDocuments();
        } catch (err) {
            showUploadStatus("error", err.message);
        }
    }

    function showUploadStatus(type, msg) {
        uploadSt.className = `upload-status ${type}`;
        uploadSt.textContent = msg;
        uploadSt.classList.remove("hidden");
        if (type === "success") {
            setTimeout(() => uploadSt.classList.add("hidden"), 4000);
        }
    }

    // =========================================================
    // Documents
    // =========================================================
    async function loadDocuments() {
        try {
            const res = await fetch("/api/documents");
            if (!res.ok) throw new Error(await readError(res));
            documents = await res.json();
            renderDocuments();
        } catch (err) {
            console.error("Failed to load documents:", err);
        }
    }

    function renderDocuments() {
        docListEl.innerHTML = "";

        if (!documents.length) {
            const li = document.createElement("li");
            li.className = "doc-empty";
            li.textContent = "No documents yet";
            docListEl.appendChild(li);
            return;
        }

        documents.forEach((d) => {
            const li = document.createElement("li");
            li.className = "doc-item";
            if (activeDocId === d.docId) li.classList.add("active");

            const main = document.createElement("div");
            main.className = "doc-item__main";
            main.innerHTML = `
        <div class="doc-item__name" title="${escapeHtml(d.fileName)}">
          ${escapeHtml(d.fileName)}
        </div>
        <div class="doc-item__meta">${d.chunkCount} chunks · ${fmtDate(d.uploadedAt)}</div>
      `;
            main.addEventListener("click", () => selectDoc(d.docId));

            const del = document.createElement("button");
            del.className = "doc-item__del";
            del.title = "Delete";
            del.textContent = "✕";
            del.addEventListener("click", (e) => {
                e.stopPropagation();
                deleteDocument(d.docId, d.fileName);
            });

            li.append(main, del);
            docListEl.appendChild(li);
        });
    }

    function selectDoc(docId) {
        activeDocId = activeDocId === docId ? null : docId;
        const doc = documents.find((d) => d.docId === activeDocId);
        scopeLabel.textContent = doc ? `📄 ${doc.fileName}` : "All documents";
        renderDocuments();
    }

    async function deleteDocument(docId, fileName) {
        if (!confirm(`Delete "${fileName}" and all its chunks?`)) return;
        try {
            const res = await fetch(`/api/documents/${docId}`, { method: "DELETE" });
            if (!res.ok) throw new Error(await readError(res));
            if (activeDocId === docId) selectDoc(docId); // clears active
            await loadDocuments();
        } catch (err) {
            alert("Delete failed: " + err.message);
        }
    }

    refreshBtn.addEventListener("click", loadDocuments);

    // =========================================================
    // Chat
    // =========================================================
    chatForm.addEventListener("submit", (e) => {
        e.preventDefault();
        sendQuestion();
    });

    questionEl.addEventListener("keydown", (e) => {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            sendQuestion();
        }
    });

    // Auto-grow textarea
    questionEl.addEventListener("input", () => {
        questionEl.style.height = "auto";
        questionEl.style.height = Math.min(questionEl.scrollHeight, 160) + "px";
    });

    async function sendQuestion() {
        const q = questionEl.value.trim();
        if (!q || busy) return;

        clearWelcome();
        appendBubble("user", q);

        questionEl.value = "";
        questionEl.style.height = "auto";

        const assistantBubble = appendBubble("assistant", "", true);
        setBusy(true);

        try {
            const res = await fetch("/api/chat/ask", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ question: q, docId: activeDocId }),
            });
            if (!res.ok) throw new Error(await readError(res));
            const data = await res.json();
            renderAssistant(assistantBubble, data);
        } catch (err) {
            assistantBubble.classList.remove("typing");
            assistantBubble.classList.add("error");
            assistantBubble.textContent = "⚠ " + err.message;
        } finally {
            setBusy(false);
        }
    }

    function renderAssistant(bubble, data) {
        bubble.classList.remove("typing");
        bubble.textContent = data.answer || "(no answer)";

        if (data.sources?.length) {
            const details = document.createElement("details");
            details.className = "sources";
            details.innerHTML = `<summary>Sources (${data.sources.length})</summary>`;

            // De-dup consecutive same-page hits for a cleaner list
            data.sources.forEach((s) => {
                const pageLabel = s.pageNumber != null
                    ? ` — page ${s.pageNumber}`
                    : "";

                const div = document.createElement("div");
                div.className = "source";
                div.innerHTML = `
        <span class="source__score">${(s.score * 100).toFixed(0)}%</span>
        <span class="source__file">
          ${escapeHtml(s.fileName || "unknown")}${pageLabel}
        </span>
        ${escapeHtml(s.snippet || "")}
      `;
                details.appendChild(div);
            });
            bubble.appendChild(details);
        }
    }

    function appendBubble(kind, text, typing = false) {
        const el = document.createElement("div");
        el.className = `bubble ${kind}${typing ? " typing" : ""}`;
        el.textContent = text;
        messagesEl.appendChild(el);
        messagesEl.scrollTop = messagesEl.scrollHeight;
        return el;
    }

    function clearWelcome() {
        const w = messagesEl.querySelector(".welcome");
        if (w) w.remove();
    }

    function setBusy(state) {
        busy = state;
        sendBtn.disabled = state;
        questionEl.disabled = state;
    }

    clearBtn.addEventListener("click", () => {
        messagesEl.innerHTML = "";
        const w = document.createElement("div");
        w.className = "welcome";
        w.innerHTML = `
      <h2>👋 Welcome</h2>
      <p>Upload a PDF on the left, then ask anything about it.</p>
      <p class="muted">Everything runs locally — no data leaves your machine.</p>
    `;
        messagesEl.appendChild(w);
    });

    // =========================================================
    // Utilities
    // =========================================================
    async function readError(res) {
        try {
            const j = await res.json();
            return j.message || res.statusText;
        } catch {
            return res.statusText;
        }
    }

    function escapeHtml(s) {
        return String(s)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#39;");
    }

    function fmtDate(iso) {
        if (!iso) return "";
        try {
            return new Date(iso).toLocaleString();
        } catch {
            return iso;
        }
    }

    // =========================================================
    // Bootstrap
    // =========================================================
    loadDocuments();
})();