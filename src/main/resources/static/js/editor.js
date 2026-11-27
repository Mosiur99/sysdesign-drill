(() => {
    const TARGET_SECONDS = 45 * 60;
    const form = document.getElementById('answer-form');
    const sections = [...document.querySelectorAll('[data-section]')];
    const textareas = sections.map((section) => section.querySelector('textarea'));
    const saveState = document.querySelector('[data-save-state]');
    const timer = document.querySelector('[data-timer]');
    const select = document.querySelector('[data-section-select]');
    const confirmBox = document.querySelector('[data-confirm]');
    let lastSaved = null;
    let saveFailed = false;
    let confirmed = false;
    let pendingSubmitter = null;

    const csrfToken = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content;
    document.body.addEventListener('htmx:configRequest', (event) => {
        if (csrfToken && csrfHeader) {
            event.detail.headers[csrfHeader] = csrfToken;
        }
    });

    const countWords = (text) => {
        const trimmed = text.trim();
        return trimmed === '' ? 0 : trimmed.split(/\s+/).length;
    };

    const titleOf = (section) => section.querySelector('label').textContent.replace(/^\d+\s+/, '');

    const updateCounts = () => {
        let written = 0;
        textareas.forEach((textarea) => {
            const words = countWords(textarea.value);
            if (words > 0) {
                written += 1;
            }
            const count = document.querySelector(`[data-count-for="${textarea.name}"]`);
            count.textContent = words > 0 ? `${words} w` : 'empty';
            count.closest('a').classList.toggle('is-empty', words === 0);
        });
        document.querySelector('[data-written]').textContent = `${written} of 8 written`;
    };

    const format = (seconds) => {
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = seconds % 60;
        const pad = (n) => String(n).padStart(2, '0');
        return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
    };

    const tick = () => {
        const elapsed = Math.max(0, Math.floor((Date.now() - Number(timer.dataset.startedAt)) / 1000));
        const over = elapsed >= TARGET_SECONDS;
        timer.firstChild.textContent = format(elapsed);
        timer.classList.toggle('is-over', over);
        const minutes = Math.floor(elapsed / 60);
        timer.setAttribute('aria-label', `Time spent: ${minutes} minutes${over ? ', over the 45 minute target' : ' of 45'}`);
        if (saveFailed) {
            saveState.textContent = 'Not saved yet, will retry';
        } else if (lastSaved) {
            const ago = Math.round((Date.now() - lastSaved) / 1000);
            saveState.textContent = ago < 5 ? 'Saved just now' : `Saved ${ago < 60 ? `${ago} s` : `${Math.floor(ago / 60)} min`} ago`;
        }
    };

    form.addEventListener('htmx:afterRequest', (event) => {
        saveFailed = !event.detail.successful;
        if (event.detail.successful) {
            lastSaved = Date.now();
        }
        saveState.classList.toggle('is-warning', saveFailed);
        tick();
    });
    form.addEventListener('input', () => {
        if (!saveFailed) {
            saveState.textContent = 'Editing';
        }
        updateCounts();
    });

    const show = (key) => {
        sections.forEach((section) => section.classList.toggle('current', section.dataset.section === key));
        select.value = key;
    };
    const step = (delta) => {
        const index = sections.findIndex((section) => section.classList.contains('current'));
        const next = Math.min(sections.length - 1, Math.max(0, index + delta));
        show(sections[next].dataset.section);
        sections[next].querySelector('textarea').focus();
    };
    select.addEventListener('change', () => show(select.value));
    document.querySelector('[data-prev]').addEventListener('click', () => step(-1));
    document.querySelector('[data-next]').addEventListener('click', () => step(1));

    form.addEventListener('submit', (event) => {
        const empty = sections.filter((section) => countWords(section.querySelector('textarea').value) === 0);
        if (confirmed || empty.length === 0) {
            return;
        }
        event.preventDefault();
        pendingSubmitter = event.submitter;
        const names = empty.map(titleOf).join(', ');
        const noun = empty.length === 1 ? 'section is' : 'sections are';
        confirmBox.querySelector('[data-confirm-text]').textContent =
            `${empty.length} ${noun} still empty: ${names}. Empty sections score 0. You can't edit after submitting.`;
        confirmBox.hidden = false;
        confirmBox.scrollIntoView({block: 'center'});
        confirmBox.querySelector('[data-confirm-yes]').focus();
    });
    confirmBox.querySelector('[data-confirm-yes]').addEventListener('click', () => {
        confirmed = true;
        form.requestSubmit(pendingSubmitter);
    });
    confirmBox.querySelector('[data-confirm-no]').addEventListener('click', () => {
        confirmBox.hidden = true;
        textareas.find((textarea) => countWords(textarea.value) === 0)?.focus();
    });

    const source = document.querySelector('[data-diagram-source]');
    const preview = document.querySelector('[data-diagram-preview]');
    if (source && preview && window.mermaid) {
        window.mermaid.initialize({startOnLoad: false, securityLevel: 'strict', theme: 'neutral'});
        let renderCount = 0;
        let debounce = null;
        const render = async () => {
            const text = source.value.trim();
            if (!/^(flowchart|graph)\b/.test(text)) {
                preview.innerHTML = '<p class="meta">The preview appears when you write a Mermaid flowchart.</p>';
                return;
            }
            try {
                renderCount += 1;
                const {svg} = await window.mermaid.render(`diagram-${renderCount}`, text);
                preview.innerHTML = svg;
                preview.classList.remove('has-error');
            } catch (error) {
                preview.classList.add('has-error');
                preview.innerHTML = '<p class="meta">The diagram has a syntax error. The last line you changed is the usual cause.</p>';
            }
        };
        source.addEventListener('input', () => {
            clearTimeout(debounce);
            debounce = setTimeout(render, 600);
        });
        render();
    }

    if (window.matchMedia('(max-width: 760px)').matches) {
        document.querySelector('.problem-box').open = false;
    }

    updateCounts();
    tick();
    setInterval(tick, 1000);
})();
