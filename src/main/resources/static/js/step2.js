function getState() {
    return JSON.parse(sessionStorage.getItem('builderState') || '{"s2":[]}');
}

function saveState(state) {
    sessionStorage.setItem('builderState', JSON.stringify(state));
}

// ---------- INIT ----------
window.addEventListener('DOMContentLoaded', () => {

    const state = getState();

    // restore selected plan
    if (state.s2 && state.s2.length > 0) {
        renderCard(state.s2[0]);
    }

    // restore selected pills
    const saved = JSON.parse(sessionStorage.getItem('selectedSvcs_s2') || '[]');

    saved.forEach(svc => {
        const pill = document.querySelector(`.svc-pill[data-svc="${svc}"]`);
        if (pill) pill.classList.add('active');
        if (!selectedSvcs.includes(svc)) selectedSvcs.push(svc);
    });

    if (saved.length) refreshSidebar();
});

// ---------- SERVICE SELECTION ----------
let selectedSvcs = [];

function toggleSvc(service, el) {

    if (selectedSvcs.includes(service)) {
        selectedSvcs = selectedSvcs.filter(s => s !== service);
        el.classList.remove('active');
    } else {
        selectedSvcs.push(service);
        el.classList.add('active');
    }

    sessionStorage.setItem('selectedSvcs_s2', JSON.stringify(selectedSvcs));

    if (selectedSvcs.length === 0) {
        clearCenter();
    }
    // else {
    //     // optional: validate if current center plan still valid
    //     validateCenterPlan();
    // }

    refreshSidebar();
}

function clearCenter() {
    const state = getState();

    state.s2 = [];
    saveState(state);

    document.getElementById('main-area').innerHTML = '';
}

function validateCenterPlan() {

    const state = getState();
    if (!state.s2 || state.s2.length === 0) return;

    const current = state.s2[0];

    const svcMap = {
        '101': 'VOICE',
        '104': 'VOICE',
        '102': 'SMS',
        '103': 'DATA'
    };

    const planSvc = svcMap[current.id];

    // if selected service no longer matches
    if (!selectedSvcs.includes(planSvc)) {
        clearCenter();
    }
}

// ---------- SIDEBAR ----------
function refreshSidebar() {

    const list = document.getElementById('comp-list');

    const svcMap = { VOICE: '1', SMS: '2', DATA: '3' };

    const types = selectedSvcs.map(s => svcMap[s]).sort().join(",");

    if (!types) {
        list.innerHTML = '';
        return;
    }

    list.innerHTML = '<p class="sidebar-text">Loading...</p>';

    fetch(`/builder/step2/filter?types=${types}`)
        .then(res => res.json())
        .then(data => {

            if (!data || !data.length) {
                list.innerHTML = '<p class="sidebar-text">No Plans</p>';
                return;
            }

            list.innerHTML = data.map(plan => `

                <div class="draggable-item"
                     onclick="addToCenter('${plan.servicePackageId}', '${plan.servicePackageName}')">
                    ${plan.servicePackageName}
                </div>

            `).join('');
        })
        .catch(err => {
            console.error(err);
            list.innerHTML = '<p class="sidebar-text">Error loading data</p>';
        });
}

// ---------- ADD (ONLY ONE ITEM) ----------
function addToCenter(id, name) {

    const state = getState();

    // REPLACE instead of push
    state.s2 = [{
        id: id,
        name: name
    }];

    saveState(state);

    // clear UI
    document.getElementById('main-area').innerHTML = '';

    renderCard(state.s2[0]);
}

// ---------- RENDER ----------
function renderCard(item) {

    const container = document.getElementById('main-area');

    const card = document.createElement('div');
    card.className = 'service-card';
    card.id = `card-s2-${item.id}`;

    card.innerHTML = `

        <div style="display:flex; justify-content:space-between; border-bottom:1px solid #eee; padding-bottom:10px;">
            <b class="service-title">${item.name}</b>
            <span onclick="removeItem()" style="color:red; cursor:pointer; font-weight:800;">✕</span>
        </div>
        
    `;

    container.appendChild(card);
}

// ---------- REMOVE ----------
function removeItem() {

    const state = getState();

    state.s2 = []; // clear
    saveState(state);

    document.getElementById('main-area').innerHTML = '';
}