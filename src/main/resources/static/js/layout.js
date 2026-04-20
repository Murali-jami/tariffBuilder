window.addEventListener("pageshow", () => {
    window.isInternalNavigation = false;
});

window.isInternalNavigation = false;

function saveDraftOnExit() {
    if (window.isInternalNavigation) return;

    const isBuilderPage = window.location.pathname.startsWith('/builder/step');
    if (!isBuilderPage) return;

    const state = JSON.parse(sessionStorage.getItem('state') || '{}');
    const configName = sessionStorage.getItem('configName');
    const pkgType = sessionStorage.getItem('pkgType');

    const hasData = pkgType || configName ||
        state?.s2?.length || state?.s3?.length || state?.s4?.length;

    if (!hasData) return;

    const now = new Date();
    const savedOn = now.toLocaleDateString('en-GB', {
        day: '2-digit', month: 'short', year: 'numeric'
    });
    const savedTime = now.toLocaleTimeString('en-GB', {
        hour: '2-digit', minute: '2-digit'
    });

    const payload = JSON.stringify({
        name: configName || 'Untitled Draft',
        pkgType,
        pkgSubType: sessionStorage.getItem('pkgSubType'),
        savedOn,
        savedTime,
        selectedSvcs_s2: sessionStorage.getItem('selectedSvcs_s2'),
        selectedSvcs_s3: sessionStorage.getItem('selectedSvcs_s3'),
        selectedSvcs_s4: sessionStorage.getItem('selectedSvcs_s4'),
        state
    });

    navigator.sendBeacon('/draft/save', new Blob([payload], { type: 'application/json' }));
}

window.addEventListener("beforeunload", saveDraftOnExit);

function openDrafts() {
    const overlay = document.getElementById('draftOverlay');

    // Step 1: make it display:block but panel still off-screen (no 'active' yet)
    overlay.style.display = 'block';

    // Step 2: double rAF so browser paints the display:block frame first,
    // then adds 'active' — this lets the CSS transition actually animate
    requestAnimationFrame(() => {
        requestAnimationFrame(() => {
            overlay.classList.add('active');
        });
    });

    loadDrafts('draftOverlayList');
}

function closeDrafts() {
    const overlay = document.getElementById('draftOverlay');
    overlay.classList.remove('active');

    // Hide after slide-out transition completes
    overlay.addEventListener('transitionend', function handler() {
        if (!overlay.classList.contains('active')) {
            overlay.style.display = 'none';
        }
        overlay.removeEventListener('transitionend', handler);
    });
}

document.addEventListener('click', function (e) {
    if (e.target.id === 'draftOverlay') {
        closeDrafts();
    }
});

function loadDrafts(targetId = 'comp-list') {

    const container = document.getElementById(targetId);
    container.innerHTML = '<p class="sidebar-text">Loading...</p>';

    fetch('/draft/list')
        .then(res => res.json())
        .then(drafts => {

            if (!drafts.length) {
                container.innerHTML = `
                    <div class="drafts-empty">
                        <span class="material-icons">edit_note</span>
                        <p class="drafts-empty-title">No drafts yet</p>
                        <p class="drafts-empty-sub">
                            Your in-progress packages will appear here
                        </p>
                    </div>
                `;
                return;
            }

            window.ALL_DRAFTS = drafts;

            container.innerHTML = drafts.map((d, i) => `
                <div class="draft-item" style="--i:${i}">
                    <div class="draft-info" onclick="loadDraft(${i})">
                        <span class="material-icons draft-icon">description</span>
                        <div class="draft-text">
                            <span class="draft-name">${d.name || 'Untitled'}</span>
                            <span class="draft-meta">${d.savedOn} · ${d.savedTime}</span>
                        </div>
                    </div>
                    <span class="material-icons draft-delete"
                          onclick="deleteDraft(${i}, event)">delete_outline</span>
                </div>
            `).join('');
        })
        .catch(() => {
            container.innerHTML = '<p class="sidebar-text">Error loading drafts</p>';
        });
}

function loadDraft(index) {
    const draft = window.ALL_DRAFTS[index];

    sessionStorage.setItem('state', JSON.stringify(draft.state || {}));
    sessionStorage.setItem('configName', draft.name || '');
    sessionStorage.setItem('pkgType', draft.pkgType || '');
    sessionStorage.setItem('pkgSubType', draft.pkgSubType || '');

    // ← restore actual saved svc selections so pills + sidebar reload correctly
    sessionStorage.setItem('selectedSvcs_s2', draft.selectedSvcs_s2 || '[]');
    sessionStorage.setItem('selectedSvcs_s3', draft.selectedSvcs_s3 || '[]');
    sessionStorage.setItem('selectedSvcs_s4', draft.selectedSvcs_s4 || '[]');

    window.isInternalNavigation = true;
    window.location.href = '/builder/step1';
}

async function deleteDraft(index, e) {
    e.stopPropagation();
    const draft = window.ALL_DRAFTS[index];
    if (!confirm(`Delete draft "${draft.name}"?`)) return;

    await fetch('/draft/save', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...draft, _delete: true })
    });

    // Remove from in-memory array and re-render without a network round-trip
    window.ALL_DRAFTS.splice(index, 1);

    // Determine which container is currently active
    const overlayOpen = document.getElementById('draftOverlay')?.classList.contains('active');
    const targetId = overlayOpen ? 'draftOverlayList' : 'comp-list';

    if (!window.ALL_DRAFTS.length) {
        document.getElementById(targetId).innerHTML = `
            <div class="drafts-empty">
                <span class="material-icons">edit_note</span>
                <p class="drafts-empty-title">No drafts yet</p>
                <p class="drafts-empty-sub">Your in-progress packages will appear here</p>
            </div>
        `;
        return;
    }

    // Re-render with corrected indices
    document.getElementById(targetId).innerHTML = window.ALL_DRAFTS.map((d, i) => `
        <div class="draft-item" style="--i:${i}">
            <div class="draft-info" onclick="loadDraft(${i})">
                <span class="material-icons draft-icon">description</span>
                <div class="draft-text">
                    <span class="draft-name">${d.name || 'Untitled'}</span>
                    <span class="draft-meta">${d.savedOn} · ${d.savedTime}</span>
                </div>
            </div>
            <span class="material-icons draft-delete"
                  onclick="deleteDraft(${i}, event)">delete_outline</span>
        </div>
    `).join('');
}

function manualSaveDraft() {
    const state = JSON.parse(sessionStorage.getItem('state') || '{}');

    const configName = sessionStorage.getItem('configName');

    const pkgType = sessionStorage.getItem('pkgType');
    const hasData = pkgType || configName ||

        state?.s2?.length || state?.s3?.length || state?.s4?.length;

    if (!hasData) {
        alert("Nothing to save as draft");
        return;
    }
    if (!configName) {
        alert("Please enter config Name to save");
        return;
    }
    const now = new Date();

    const savedOn = now.toLocaleDateString('en-GB', {
        day: '2-digit', month: 'short', year: 'numeric'
    });

    const savedTime = now.toLocaleTimeString('en-GB', {
        hour: '2-digit', minute: '2-digit'
    });
    const payload = {

        name: configName,

        pkgType,

        pkgSubType: sessionStorage.getItem('pkgSubType'),

        savedOn,

        savedTime,

        selectedSvcs_s2: sessionStorage.getItem('selectedSvcs_s2'),

        selectedSvcs_s3: sessionStorage.getItem('selectedSvcs_s3'),

        selectedSvcs_s4: sessionStorage.getItem('selectedSvcs_s4'),

        state

    };
    fetch('/draft/save', {

        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)

    })
        .then(() => {
            alert("Draft saved successfully ✅");
        })

        .catch(() => {
            alert("Failed to save draft ❌");
        });
}

function applyPrivilege() {

    const builderNode = document.getElementById('mn-builder');
    const approverNode = document.getElementById('mn-approver');

    const hasBuilder = PRIVILEGE_IDS.includes("P26125");
    const hasApprover = PRIVILEGE_IDS.includes("P26126");

    // Hide nodes individually
    if (!hasBuilder && builderNode) {
        builderNode.style.display = "none";
    }

    if (!hasApprover && approverNode) {
        approverNode.style.display = "none";
    }
}

/*async function checkDraftsOnLogin() {
    const isBuilderPage = window.location.pathname.startsWith('/builder/step');
    if (!isBuilderPage) return;
    if (!window.location.pathname.includes('step1')) return;

    const state = JSON.parse(sessionStorage.getItem('state') || '{}');
    const pkgType = sessionStorage.getItem('pkgType');
    if (pkgType || state?.s2?.length) return;

    try {
        const res = await fetch('/draft/list');
        const drafts = await res.json();

        if (!drafts.length) return;

        // OPEN DRAFT PANEL
        openDrafts();

    } catch (err) {
        console.error('Draft check failed', err);
    }
}*/

// ── Apply privilege on load ──
window.addEventListener('DOMContentLoaded', () => {

    applyPrivilege();
    restoreActiveModule();
    restoreConfigName();

    // mark all step navigation as internal so draft save is skipped
    document.querySelectorAll('.step-node, .main-node').forEach(link => {
        link.addEventListener('click', () => {
            window.isInternalNavigation = true;
        });
    });
});

// ── Restore active module based on current URL ──
function restoreActiveModule() {

    const hasBuilder = PRIVILEGE_IDS.includes("P26125");
    const hasApprover = PRIVILEGE_IDS.includes("P26126");

    const path = window.location.pathname;

    // If already in admin
    if (path.startsWith('/builder/admin')) {
        setModuleUI('approver');
        return;
    }

    // If already in builder
    if (path.startsWith('/builder/step')) {
        setModuleUI('builder');
        return;
    }

    // FIRST LOAD DECISION
    if (!hasBuilder && hasApprover) {
        window.isInternalNavigation = true;
        window.location.href = "/builder/admin";
    } else if (hasBuilder) {
        window.isInternalNavigation = true;
        window.location.href = "/builder/step1";
    }
}

// ── Activate module (called from main-rail anchor click) ──
function activateModule(module, el) {

    const hasBuilder = PRIVILEGE_IDS.includes("P26125");
    const hasApprover = PRIVILEGE_IDS.includes("P26126");

    if (module === 'builder' && !hasBuilder) return false;
    if (module === 'approver' && !hasApprover) return false;

    // RESTORE LIBRARY when switching back
    if (module === 'builder') {
        const container = document.getElementById('comp-list');

        if (window._libraryCache !== undefined) {
            container.innerHTML = window._libraryCache;
        } else if (typeof refreshSidebar === 'function') {
            refreshSidebar();
        }
    }

    setModuleUI(module);
    return true;
}

function setModuleUI(module) {

    const stepRail = document.getElementById('stepRail');
    const sidebar = document.getElementById('sidebar');
    const builderNode = document.getElementById('mn-builder');
    const approverNode = document.getElementById('mn-approver');
    const configInput = document.getElementById('configName');

    // ── Active node highlight ──
    if (builderNode) builderNode.classList.toggle('active', module === 'builder');
    if (approverNode) approverNode.classList.toggle('active', module === 'approver');

    if (module === 'builder') {
        // Show step rail always for builder
        if (stepRail) stepRail.classList.remove('collapsed');
        if (footerActions) footerActions.style.display = 'flex';
        if (configInput) configInput.style.display = 'block';

        // Sidebar visible only on steps 2, 3, 4
        const step = getActiveStep();
        if (sidebar) {
            if (step === 2 || step === 3 || step === 4) {
                sidebar.classList.remove('collapsed');
                // Ensure search bar is present once sidebar is visible
                requestAnimationFrame(() => initLibrarySearch());
            } else {
                sidebar.classList.add('collapsed');
            }
        }

        // Hierarchy button visible only on step 5
        applyHierarchyButtonVisibility(step);

    } else {
        // Approver — collapse step rail + sidebar (they're irrelevant)
        if (stepRail) stepRail.classList.add('collapsed');
        if (sidebar) sidebar.classList.add('collapsed');
        if (footerActions) footerActions.style.display = 'none';
        if (configInput) configInput.style.display = 'none';
    }
}

// Returns the current active step number (1-5) from the URL, or 0 for non-step pages
function getActiveStep() {
    const match = window.location.pathname.match(/step(\d)/);
    return match ? parseInt(match[1], 10) : 0;
}

// Shows/hides the Hierarchy button — only visible on step 5
function applyHierarchyButtonVisibility(step) {
    // The Hierarchy button is the first btn-hierarchy in footerActions
    const hierarchyBtn = document.querySelector('#footerActions .btn-hierarchy');
    if (hierarchyBtn) {
        hierarchyBtn.style.display = (step === 5) ? 'inline-flex' : 'none';
    }
}

// ═══════════════════════════════════════════════════════
//  LIBRARY SEARCH
// ═══════════════════════════════════════════════════════

// Call this once the sidebar is populated (from step JS after loadLibrary/refreshSidebar)
function initLibrarySearch() {
    const sidebar = document.getElementById('sidebar');
    if (!sidebar) return;

    // Don't inject twice
    if (document.getElementById('librarySearchInput')) return;

    const searchWrapper = document.createElement('div');
    searchWrapper.id = 'librarySearchWrapper';
    searchWrapper.innerHTML = `
        <div class="library-search-box">
            <span class="material-icons library-search-icon">search</span>
            <input
                id="librarySearchInput"
                class="library-search-input"
                type="text"
                placeholder="Search packages..."
                autocomplete="off"
            />
            <span class="material-icons library-search-clear" id="librarySearchClear"
                  onclick="clearLibrarySearch()" title="Clear">close</span>
        </div>
    `;

    // Insert before the sidebar-content div
    const content = document.getElementById('comp-list');
    if (content && content.parentNode) {
        content.parentNode.insertBefore(searchWrapper, content);
    }

    document.getElementById('librarySearchInput').addEventListener('input', function () {
        filterLibraryItems(this.value.trim());
        const clearBtn = document.getElementById('librarySearchClear');
        if (clearBtn) clearBtn.style.opacity = this.value ? '1' : '0';
    });
}

function clearLibrarySearch() {
    const input = document.getElementById('librarySearchInput');
    if (input) {
        input.value = '';
        input.dispatchEvent(new Event('input'));
        input.focus();
    }
}

function hasTrigramMatch(text, query) {

    if (!query) return true;

    return text
        .toLowerCase()
        .includes(
            query.toLowerCase()
        );
}

function filterLibraryItems(query) {

    const container =

        document.getElementById('comp-list');

    if (!container) return;

    const items =

        container.querySelectorAll(

            '[data-name], .comp-card, .service-card, .library-item, .comp-item, .sidebar-item'

        );

    let visibleCount = 0;


    if (!items.length) {

        const children =

            container.children;

        Array.from(children).forEach(el => {

            const name =

                el.dataset.name ||

                el.textContent ||

                '';

            const match =

                hasTrigramMatch(name, query);

            el.style.display =

                match ? '' : 'none';

            if (match) visibleCount++;

        });

    } else {

        items.forEach(el => {

            const name =

                el.dataset.name ||

                el.querySelector('[data-name]')?.dataset.name ||

                el.querySelector(

                    '.comp-name, .item-name, .service-name, .card-title'

                )?.textContent ||

                el.textContent;

            const match = hasTrigramMatch(name, query);

            el.style.display = match ? '' : 'none';

            if (match) visibleCount++;

        });
    }

    // remove old message if exists
    const oldMsg = document.getElementById('noResultsMsg');

    if (oldMsg) oldMsg.remove();

    // show message if no match
    if (visibleCount === 0 && query) {

        const msg = document.createElement('div');

        msg.id = 'noResultsMsg';

        msg.className = 'no-results';

        msg.innerHTML = 'No Results Found';

        container.appendChild(msg);
    }
}

function getState() {

    const defaultState = {
        s2: [],
        s3: [],
        s4: [],
        price: "",
        publicityCode: "",
        endDate: "",
        isCorporate: false
    };

    const stored = sessionStorage.getItem('state');
    return stored ? JSON.parse(stored) : defaultState;
}

function saveState(state) {
    sessionStorage.setItem('state', JSON.stringify(state));
}

// ── Restore config name across steps ──
function restoreConfigName() {

    const input = document.getElementById('configName');
    if (!input) return;

    const savedName = sessionStorage.getItem('configName') || '';
    input.value = savedName;

    input.addEventListener('input', () => {
        sessionStorage.setItem('configName', input.value);
    });
}

function resetBuilder() {
    const state = JSON.parse(sessionStorage.getItem('state') || '{}');
    const pkgType = sessionStorage.getItem('pkgType');
    const configName = sessionStorage.getItem('configName');

    const isEmpty = !pkgType && !configName &&
        !state.s2?.length && !state.s3?.length && !state.s4?.length;

    if (isEmpty) {
        alert('Nothing to reset — Builder is already empty.');
        return;
    }

    if (!confirm('Reset all selections and start over?')) return;
    clearBuilderSession();
    window.isInternalNavigation = true;
    window.location.href = '/builder/step1';
}

// ═══════════════════════════════════════════════════════
//  STEP ACCESS GUARD
// ═══════════════════════════════════════════════════════
function checkStepAccess(targetStep) {

    const currentPath = window.location.pathname;

    if (currentPath.includes(`step${targetStep}`)) return true;
    if (targetStep === 1) return true;

    const pkgType = sessionStorage.getItem('pkgType');
    const state = JSON.parse(sessionStorage.getItem('state') || '{}');

    if (!pkgType) {
        alert("Please select PREPAID or POSTPAID in Step 1");
        return false;
    }

    const hasStep2Data = state.s2 && Array.isArray(state.s2) && state.s2.length > 0;

    if (targetStep > 2 && !hasStep2Data) {
        alert("Please select Service Plan in Step 2");
        return false;
    }

    return true;
}

// ═══════════════════════════════════════════════════════
//  USER MENU
// ═══════════════════════════════════════════════════════
function toggleUserMenu() {
    const dropdown = document.getElementById("userDropdown");
    dropdown.classList.toggle("active");
}

document.addEventListener("click", function (e) {
    const menu = document.querySelector(".user-menu");
    if (menu && !menu.contains(e.target)) {
        const dd = document.getElementById("userDropdown");
        if (dd) dd.classList.remove("active");
    }
});

// ═══════════════════════════════════════════════════════
//  SAVE PACKAGE CONFIGURATION
// ═══════════════════════════════════════════════════════
async function saveConfiguration() {

    const configName = document.getElementById("configName").value;

    if (!configName) {
        alert("Enter Configuration Name");
        return;
    }

    const state = JSON.parse(sessionStorage.getItem("state"));

    const isUpdate = sessionStorage.getItem("isUpdate") === "true";


    if (!state?.s2?.length) {
        alert("Step 2 required");
        return;
    }

    if (!state.price) {
        alert("Enter charge amount");
        return;
    }

    if (!state.endDate) {
        alert("Select end date");
        return;
    }

    if (!state.publicityCode) {
        alert("Enter publicity code");
        return;
    }

    const chargeId = configName + "_PR";

    function formatDateToMMDDYYYY(dateStr) {

        if (!dateStr) return "12/31/2030";

        const [year, month, day] =
            dateStr.split("-");

        return `${month}/${day}/${year}`;
    }

    const payload = {

        username: USERNAME,

        isUpdate: isUpdate,

        submittedOn: new Date().toLocaleDateString('en-GB', {
            day: '2-digit',
            month: 'short',
            year: 'numeric'
        }),

        packageType: sessionStorage.getItem("pkgType"),

        tariffPackCategory: sessionStorage.getItem("pkgSubType") ||
            "GENERAL",

        tariffPackageDesc: configName,

        charge: state.price,

        endDate: formatDateToMMDDYYYY(state.endDate),

        publicityId: state.publicityCode,

        chargeId: chargeId,

        isCorporateYn: state.isCorporate,

        tariffPlanId: Number(state.s2[0].id),

        tariffPlanName: state.s2[0].name,

        selectedSvcs_s2: sessionStorage.getItem('selectedSvcs_s2') || '[]',

        selectedSvcs_s3: sessionStorage.getItem('selectedSvcs_s3') || '[]',

        selectedSvcs_s4: sessionStorage.getItem('selectedSvcs_s4') || '[]',

        defaultAtps: state.s3.map(item => ({

            servicePackageId: Number(item.id),

            chargeId: chargeId,

            packageName: item.name,

            validity: item.validity,

            midnightExpiry: item.midnightExpiry,

            renewal: item.renewal,

            rental: item.rental || 0,

            maxCount: item.maxCount || 0,

            freeCycles: item.freeCycles || 0
        })),

        allowedAtps: state.s4.map(item => ({

            servicePackageId: Number(item.id),

            chargeId: chargeId,

            packageName: item.name,

            validity: item.validity,

            midnightExpiry: item.midnightExpiry,

            renewal: item.renewal,

            rental: item.rental || 0,

            maxCount: item.maxCount || 0,

            freeCycles: item.freeCycles || 0
        }))
    };

    console.log("REQUEST", payload);

    try {

        const response =
            await fetch("/prepareSaveConfig", {

                method: "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify(payload)
            });

        const result = await response.json();

        if (!response.ok || result.error) {

            alert(result.error || "Validation failed");
            return;
        }
        alert(result.message);

        clearBuilderSession();

        window.isInternalNavigation = true;

        window.location.href =
            "/builder/step1";
    } catch (error) {
        console.error(error);
        alert("Server error");
    }
}

function clearBuilderSession() {
    sessionStorage.removeItem('state');
    sessionStorage.removeItem('selectedSvcs_s2');
    sessionStorage.removeItem('selectedSvcs_s3');
    sessionStorage.removeItem('selectedSvcs_s4');
    sessionStorage.removeItem('configName');
    sessionStorage.removeItem('pkgType');
    sessionStorage.removeItem('pkgSubType');
    sessionStorage.removeItem("isUpdate");
}

// ═══════════════════════════════════════════════════════
//  HIERARCHY MODAL
// ═══════════════════════════════════════════════════════
function viewTree() {
    const state = getState();
    const name = document.getElementById('configName').value || 'Unnamed Package';
    const type = sessionStorage.getItem('pkgType') || '';
    const sub = sessionStorage.getItem('pkgSubType') || 'NORMAL';

    document.getElementById('treeName').textContent = name;
    document.getElementById('treeMeta').textContent = `${type ? type + ' | ' : ''}${sub} | ${state.isCorporate ? 'Corporate' : 'Retail'}`;
    document.getElementById('treeMain').textContent = `📦 Main Service Plan: ${state.s2 && state.s2[0] ? state.s2[0].name : 'None'}`;
    document.getElementById('treeDatp').textContent = `➕ DATP Components: ${(state.s3 || []).length} items`;
    document.getElementById('treeAatp').textContent = `🛒 AATP Components: ${(state.s4 || []).length} items`;
    document.getElementById('treeCharge').innerHTML = `<b>Charge: RM ${state.price || '0.00'}</b> | <b>Ends: ${state.endDate || 'Permanent'}</b>`;

    document.getElementById('treeModal').classList.add('open');
}

function closeTree() {
    document.getElementById('treeModal').classList.remove('open');
}

document.addEventListener('keydown', e => {
    if (e.key === 'Escape') closeTree();
});

// ═══════════════════════════════════════════════════════
//  LOGOUT
// ═══════════════════════════════════════════════════════
function logout() {
    sessionStorage.clear();
    window.location.href = '/logout';
}

const storedSessionId = sessionStorage.getItem("SESSION_ID");

if (SESSION_ID && storedSessionId !== SESSION_ID) {
    clearBuilderSession(); // safer
    sessionStorage.setItem("SESSION_ID", SESSION_ID);
}

// ═══════════════════════════════════════════════════════
//  ADMIN — TWO-PANE APPROVER UI (with working arrows)
// ═══════════════════════════════════════════════════════

function selectPackage(cardElement) {
    const tpName = cardElement.dataset.tpname;
    const isAlreadySelected = cardElement.classList.contains('selected');

    document.querySelectorAll('.approval-card').forEach(c => c.classList.remove('selected'));

    if (isAlreadySelected) {
        document.getElementById('hierarchy-view').classList.add('hidden');
        document.getElementById('no-selection').classList.remove('hidden');
        return;
    }

    cardElement.classList.add('selected');
    loadHierarchy(tpName);
}

function loadHierarchy(tpName) {

    fetch('/admin/hierarchy/' + tpName)
        .then(res => res.json())
        .then(tp => {
            if (!tp || !tp.data) {
                alert("No hierarchy data found");
                return;
            }

            const data = tp.data || {};

            // Switch views
            document.getElementById('no-selection').classList.add('hidden');
            document.getElementById('no-packages')?.classList.add('hidden');
            const view = document.getElementById('hierarchy-view');
            view.classList.remove('hidden');

            // Header
            document.getElementById('h-name').textContent = data.tariffPackageDesc || tpName;

            const metaHTML = `
			<span class="meta-pill">
			        <span class="pill-label">Billing Type</span>
			        <span class="pill-value">${data.packageType}</span>
			    </span>
			    <span class="meta-pill">
			        <span class="pill-label">Category</span>
			        <span class="pill-value">${data.tariffPackCategory || 'Normal'}</span>
			    </span>
			    <span class="meta-pill">
			        <span class="pill-label">Segment</span>
			        <span class="pill-value">${data.isCorporateYn ? 'Corporate' : 'Retail'}</span>
			    </span>
			`;
            document.getElementById('h-meta').innerHTML = metaHTML;
            document.getElementById('h-meta').innerHTML = metaHTML;

            const submittedBy = data.username || '—';
            const submittedOn = data.submittedOn || '—';
            document.getElementById('h-submeta').innerHTML = `
                <span>Submitted by <strong>${data.username || '—'}</strong></span>
                <span>${data.submittedOn || '—'}</span>
            `;

            // Main Plan
            document.getElementById('h-main-header').textContent =
                `📦 ${data.tariffPlanName || 'None'}`;

            // DATP Components
            const datp = data.defaultAtps || [];
            document.getElementById('h-datp-header').textContent = `🛒 DATP - ${datp.length} COMPONENTS`;
            const datpHtml = datp.map(item => `
                <div class="component-box">
                    <div class="comp-name">${item.packageName}</div>
                    <div class="comp-details">
                        <span class="pill"><strong>Validity:</strong> ${item.validity || '—'}</span>
                        <span class="pill"><strong>Midnight Expiry:</strong> ${item.midnightExpiry || '—'}</span>
                        <span class="pill"><strong>Renewal:</strong> ${item.renewal || '—'}</span>
                        <span class="pill"><strong>Rental:</strong> ${item.rental || '0'}</span>
                        <span class="pill"><strong>Max Count:</strong> ${item.maxCount || '0'}</span>
                        <span class="pill"><strong>Free Cycles:</strong> ${item.freeCycles || '0'}</span>
                    </div>
                </div>
            `).join('');

            document.getElementById('h-datp').innerHTML = datpHtml || '<p style="color:#94a3b8; font-size:13px; padding:8px 0;">No DATP components</p>';

            // AATP Components
            const aatp = data.allowedAtps || [];
            document.getElementById('h-aatp-header').textContent = `➕ AATP - ${aatp.length} COMPONENTS`;
            const aatpHtml = aatp.map(item => `
                <div class="component-box">
                    <div class="comp-name">${item.packageName}</div>
                    <div class="comp-details">
                        <span class="pill"><strong>Validity:</strong> ${item.validity || '—'}</span>
                        <span class="pill"><strong>Midnight Expiry:</strong> ${item.midnightExpiry || '—'}</span>
                        <span class="pill"><strong>Renewal:</strong> ${item.renewal || '—'}</span>
                        <span class="pill"><strong>Rental:</strong> ${item.rental || '0'}</span>
                        <span class="pill"><strong>Max Count:</strong> ${item.maxCount || '0'}</span>
                        <span class="pill"><strong>Free Cycles:</strong> ${item.freeCycles || '0'}</span>
                    </div>
                </div>
            `).join('');

            document.getElementById('h-aatp').innerHTML = aatpHtml || '<p style="color:#94a3b8; font-size:13px; padding:8px 0;">No AATP components</p>';

            // Footer
            document.getElementById('h-footer-bar').innerHTML = `
                <div><b>Charge:</b> RM ${data.charge}</div>
                <div><b>Ends:</b> ${data.endDate}</div>
            `;
        })
        .catch(err => {
            console.error(err);
            alert("Could not load hierarchy");
        });
}

function approvePackage(tpName, btn) {

    event.stopImmediatePropagation();

    if (!confirm("Approve " + tpName + " ?"))
        return;

    fetch("/approve/" + tpName, {
        method: "POST"
    })
        .then(res => {

            if (!res.ok)
                throw new Error("Approve failed");

            return res.json();
        })
        .then(data => {

            console.log("APPROVED", data);

            alert(
                "Tariff Created : "
                + data.tariffPackageId
            );

            window.location.href = '/builder/admin';

        })
        .catch(err => {

            console.error(err);

            alert("Error approving tariff");
        });
}

/* REJECT */
function rejectPackage(tpName, btn) {

    event.stopImmediatePropagation();

    if (!confirm("Reject " + tpName + " ?"))
        return;

    fetch("/reject/" + tpName, {
        method: "POST"
    })
        .then(res => {

            if (!res.ok)
                throw new Error("Reject failed");

            return res.json();
        })
        .then(data => {

            console.log("REJECTED", data);

            alert("Rejected");

            window.location.href = '/builder/admin';

        })
        .catch(err => {

            console.error(err);

            alert("Error rejecting tariff");
        });
}

function openSaved() {

    const overlay =
        document.getElementById('savedOverlay');

    overlay.style.display = 'block';

    requestAnimationFrame(() => {
        requestAnimationFrame(() => {
            overlay.classList.add('active');
        });
    });

    loadSaved();
}

function loadSaved() {

    const container =
        document.getElementById('savedOverlayList');

    container.innerHTML =
        '<p class="sidebar-text">Loading...</p>';

    fetch('/saved/list')

        .then(res => res.json())

        .then(data => {

            // convert map → array
            const configs =
                Object.values(data);

            if (!configs.length) {

                container.innerHTML = `
 
                <div class="drafts-empty">
 
                    <span class="material-icons">
                        inventory_2
                    </span>
 
                    <p class="drafts-empty-title">
                        No saved configs
                    </p>
 
                </div>
            `;

                return;
            }

            window.ALL_SAVED =
                configs;

            container.innerHTML =

                configs.map((c, i) => `
 
                    <div class="draft-item saved">
                        <div class="draft-info"
                            onclick="loadSavedPackage(${i})">
                            <span class="material-icons draft-icon">inventory_2</span>
                            <div class="draft-text">
                                <span class="draft-name">${c.tpName}</span>
                                <span class="draft-meta">${c.username} · ${c.data?.submittedOn || ''}</span>
                            </div>
                        </div>

                        <span class="material-icons draft-delete"
                            onclick="deleteSaved('${c.tpName}', event)">
                            delete_outline
                        </span>
                    </div>
            `).join("");
        })

        .catch(() => {

            container.innerHTML =
                '<p>Error loading configs</p>';
        });
}

function loadSavedPackage(index) {

    const config =
        window.ALL_SAVED[index];

    const d =
        config.data;


    /*
       convert saved format → builder state
    */

    const state = {

        s2: [{
            id: d.tariffPlanId,
            name: d.tariffPlanName
        }],

        s3: (d.defaultAtps || []).map(a => ({

            id: a.servicePackageId,
            name: a.packageName,
            validity: a.validity,
            midnightExpiry: a.midnightExpiry,
            renewal: a.renewal,
            rental: a.rental,
            maxCount: a.maxCount,
            freeCycles: a.freeCycles

        })),

        s4: (d.allowedAtps || d.additionalAtps || []).map(a => ({

            id: a.servicePackageId,
            name: a.packageName,
            validity: a.validity,
            midnightExpiry: a.midnightExpiry,
            renewal: a.renewal,
            rental: a.rental,
            maxCount: a.maxCount,
            freeCycles: a.freeCycles

        })),

        price: d.charge,

        publicityCode: d.publicityId,

        endDate: (function () {

            if (!d.endDate) return "";

            var p = d.endDate.split("/");

            if (p.length === 3)
                return p[2] + "-" + p[0] + "-" + p[1];

            return d.endDate;
        })(),

        isCorporate: d.isCorporateYn
    };


    /*
       SAME keys as draft loader
    */

    sessionStorage.setItem(
        "state",
        JSON.stringify(state)
    );

    sessionStorage.setItem(
        "configName",
        config.tpName
    );

    sessionStorage.setItem(
        "pkgType",
        d.packageType
    );

    sessionStorage.setItem(
        "pkgSubType",
        d.tariffPackCategory
    );


    /*
       IMPORTANT for sidebar selections
    */

    sessionStorage.setItem(
        "selectedSvcs_s2",
        d.selectedSvcs_s2 || '[]'
    );

    sessionStorage.setItem(
        "selectedSvcs_s3",
        d.selectedSvcs_s3 || '[]'
    );

    sessionStorage.setItem(
        "selectedSvcs_s4",
        d.selectedSvcs_s4 || '[]'
    );

    sessionStorage.setItem("isUpdate", "true");

    window.isInternalNavigation = true;

    window.location.href =
        "/builder/step1";
}

function deleteSaved(tpName, e) {
    e.stopPropagation();

    if (!confirm(`Delete "${tpName}"?`)) return;

    fetch('/saved/delete/' + tpName, {
        method: 'POST'
    })
        .then(() => {
            // remove from UI instantly
            loadSaved(); // reload list
        })
        .catch(() => {
            alert("Delete failed ❌");
        });
}

function closeSaved() {
    const overlay = document.getElementById('savedOverlay');
    overlay.classList.remove('active');

    // Hide after slide-out transition completes
    overlay.addEventListener('transitionend', function handler() {
        if (!overlay.classList.contains('active')) {
            overlay.style.display = 'none';
        }
        overlay.removeEventListener('transitionend', handler);
    });
}

document.addEventListener('click', function (e) {
    if (e.target.id === 'savedOverlay') {
        closeSaved();
    }
});

function goBack() {
    const step = getActiveStep();

    if (step <= 1) return;

    window.isInternalNavigation = true;
    window.location.href = `/builder/step${step - 1}`;
}

function goNext() {
    const step = getActiveStep();

    if (!checkStepAccess(step + 1)) return;

    if (step >= 5) return;

    window.isInternalNavigation = true;
    window.location.href = `/builder/step${step + 1}`;
}

// ═══════════════════════════════════════════════════════
//  PLAN HOVER TOOLTIP
// ═══════════════════════════════════════════════════════

(function initPlanHoverTooltip() {

    // ── Create a single shared tooltip element ──
    const tooltip = document.createElement('div');
    tooltip.id = 'planHoverTooltip';
    tooltip.style.cssText = `
        position: fixed;
        z-index: 9999;
        background: #1e293b;
        color: #f1f5f9;
        border: 1px solid #334155;
        border-radius: 8px;
        padding: 8px 14px;
        font-size: 10px;
        line-height: 1.5;
        max-width: 280px;
        box-shadow: 0 8px 24px rgba(0,0,0,0.35);
        pointer-events: none;
        opacity: 0;
        transition: opacity 0.15s ease;
        white-space: pre-wrap;
        word-break: break-word;
    `;
    document.body.appendChild(tooltip);

    // Cache: packageId → response string (avoids duplicate API calls)
    const _tooltipCache = {};
    // Track in-flight requests to avoid duplicates
    const _inFlight = {};

    function showTooltip(text, x, y) {
        tooltip.textContent = text;
        positionTooltip(x, y);
        tooltip.style.opacity = '1';
    }

    function hideTooltip() {
        tooltip.style.opacity = '0';
    }

    function positionTooltip(x, y) {
        const GAP = 12;
        const tw = tooltip.offsetWidth || 280;
        const th = tooltip.offsetHeight || 60;
        const vw = window.innerWidth;
        const vh = window.innerHeight;

        let left = x + GAP;
        let top = y + GAP;

        if (left + tw > vw - 8) left = x - tw - GAP;
        if (top + th > vh - 8) top = y - th - GAP;

        tooltip.style.left = left + 'px';
        tooltip.style.top = top + 'px';
    }

    // ── Delegate hover on #comp-list plan cards ──
    document.addEventListener('mouseover', function (e) {
        const card = e.target.closest('[data-network-id][data-package-id], [data-networkid][data-packageid]');
        if (!card) return;

        const networkId = card.dataset.networkId || card.dataset.networkid;
        const servicePackageId = card.dataset.packageId || card.dataset.packageid;
        if (!networkId || !servicePackageId) return;

        const cacheKey = networkId + ':' + servicePackageId;

        // If cached, show immediately
        if (_tooltipCache[cacheKey]) {
            showTooltip(_tooltipCache[cacheKey], e.clientX, e.clientY);
            return;
        }

        // Show loading state while fetching
        showTooltip('Loading...', e.clientX, e.clientY);

        // Skip if already fetching
        if (_inFlight[cacheKey]) return;
        _inFlight[cacheKey] = true;

        fetch('/description', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ networkId, servicePackageId })
        })
            .then(res => res.json())   // parse JSON first
            .then(data => {
                console.log("API RESPONSE:", data);
                const description = data.description;  // adjust based on backend

                _tooltipCache[cacheKey] = description || '—';

                showTooltip(_tooltipCache[cacheKey], e.clientX, e.clientY);
            })
            .catch(() => {
                _tooltipCache[cacheKey] = 'Description unavailable';
            })
            .finally(() => {
                delete _inFlight[cacheKey];
            });
    });

    // ── Follow mouse while inside the card ──
    document.addEventListener('mousemove', function (e) {
        if (tooltip.style.opacity === '1') {
            positionTooltip(e.clientX, e.clientY);
        }
    });

    // ── Hide when leaving the card ──
    document.addEventListener('mouseout', function (e) {
        const card = e.target.closest('[data-network-id][data-package-id], [data-networkid][data-packageid]');
        if (!card) return;
        if (!card.contains(e.relatedTarget)) {
            hideTooltip();
        }
    });

})();