// ---------- Step4.js ----------
function getState() {
    return JSON.parse(sessionStorage.getItem('builderState') || '{"s4":[]}');
}

function saveState(state) {
    sessionStorage.setItem('builderState', JSON.stringify(state));
}

// ---------- INIT ----------
window.addEventListener('DOMContentLoaded', () => {

    const state = getState();

    if (!state.s4) state.s4 = [];

    state.s4.forEach(item => renderCard(item));

    const saved = JSON.parse(sessionStorage.getItem('selectedSvcs_s4') || '[]');

    saved.forEach(svc => {
        const pill = document.querySelector(`.svc-pill[data-svc="${svc}"]`);
        if (pill) pill.classList.add('active');
        if (!selectedSvcs.includes(svc)) selectedSvcs.push(svc);
    });

    if (saved.length) refreshSidebar();
});

// ---------- SERVICE TYPE ----------
let selectedSvcs = [];

function toggleSvc(service, el) {

    if (selectedSvcs.includes(service)) {
        selectedSvcs = selectedSvcs.filter(s => s !== service);
        el.classList.remove('active');
    } else {
        selectedSvcs.push(service);
        el.classList.add('active');
    }

    sessionStorage.setItem('selectedSvcs_s4', JSON.stringify(selectedSvcs));

    if (selectedSvcs.length === 0) {
        clearCenter();
    }
    // else {
    //     validateCenterPlans();
    // }

    refreshSidebar();
}

function clearCenter() {

    const state = getState();

    state.s4 = [];
    saveState(state);

    document.getElementById('dropArea').innerHTML = '';
}

function validateCenterPlans() {

    const state = getState();

    if (!state.s4 || state.s4.length === 0) return;

    const svcMap = {
        '201': 'VOICE',
        '202': 'VOICE',
        '203': 'SMS',
        '204': 'DATA',
        '205': 'DATA'
    };

    const validItems = state.s4.filter(item => {
        const svc = svcMap[item.id];
        return selectedSvcs.includes(svc);
    });

    state.s4 = validItems;
    saveState(state);

    const container = document.getElementById('dropArea');
    container.innerHTML = '';
    state.s4.forEach(item => renderCard(item));
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

    fetch(`/builder/step4/filter?types=${types}`)
        .then(res => res.json())
        .then(data => {

            if (!data || !data.length) {
                list.innerHTML = '<p class="sidebar-text">No Plans</p>';
                return;
            }

            list.innerHTML = data.map(plan => `
                <div class="draggable-item"
                     onclick="addToCenter('${plan.servicePackageId}','${plan.servicePackageName}')">
                    ${plan.servicePackageName}
                </div>
            `).join('');

        })
        .catch(err => {
            console.error(err);
            list.innerHTML = '<p class="sidebar-text">Error loading data</p>';
        });
}

// ---------- ADD ----------
function addToCenter(id, name) {

    const state = getState();
    if (!state.s4) state.s4 = [];

    if (state.s3 && state.s3.find(item => item.id === id)) {
        alert(`"${name}" is already selected in DATP`);
        return;
    }

    if (state.s4.find(i => i.id === id)) return;

    const item = {
        id: id,
        name: name,
        validity: "Monthly",
        renewal: "No",
        midnightExpiry: "No",
        rental: "",
        maxCount: "",
        freeCycles: "0"
    };

    state.s4.push(item);
    saveState(state);

    renderCard(item);
}

// ---------- RENDER ----------
function renderCard(item) {

    const container = document.getElementById('dropArea');

    const card = document.createElement('div');
    card.className = "service-card";
    card.id = `card-s4-${item.id}`;

    card.innerHTML = `
    <div style="display:flex;justify-content:space-between;margin-bottom:10px;">
        <b>${item.name}</b>
        <span onclick="removeItem('${item.id}')" style="color:red;cursor:pointer;">✕</span>
    </div>

    <div class="card-grid">
        <div class="card-field">
            <label>VALIDITY</label>
            <select onchange="updateField('${item.id}','validity',this.value)">
                <option ${item.validity === 'Monthly' ? 'selected' : ''}>Monthly</option>
                <option ${item.validity === 'Weekly' ? 'selected' : ''}>Weekly</option>
            </select>
        </div>

        <div class="card-field">
            <label>MIDNIGHT EXPIRY</label>
            <select onchange="updateField('${item.id}','midnightExpiry',this.value)">
                <option ${item.midnightExpiry === 'No' ? 'selected' : ''}>No</option>
                <option ${item.midnightExpiry === 'Yes' ? 'selected' : ''}>Yes</option>
            </select>
        </div>

        <div class="card-field">
            <label>AUTO RENEWAL</label>
            <select onchange="handleRenewalChange('${item.id}',this.value)">
                <option ${item.renewal === 'No' ? 'selected' : ''}>No</option>
                <option ${item.renewal === 'Yes' ? 'selected' : ''}>Yes</option>
            </select>
        </div>

        <div id="renewal-${item.id}" style="display:${item.renewal === 'Yes' ? 'contents' : 'none'};">
            <div class="card-field">
                <label>RENTAL</label>
                <input type="number"
                       value="${item.rental || ''}"
                       oninput="updateField('${item.id}','rental',this.value)">
            </div>

            <div class="card-field">
                <label>MAX COUNT</label>
                <input type="number"
                       value="${item.maxCount || ''}"
                       oninput="updateField('${item.id}','maxCount',this.value)">
            </div>

            <div class="card-field">
                <label>FREE CYCLES</label>
                <input type="number"
                       value="${item.freeCycles || 0}"
                       oninput="updateField('${item.id}','freeCycles',this.value)">
            </div>
        </div>
    </div>
    `;

    container.appendChild(card);
}

// ---------- UPDATE ----------
function updateField(id, key, value) {

    const state = getState();
    const item = state.s4.find(i => i.id === id);

    if (item) {
        item[key] = value;
        saveState(state);
    }
}

// ---------- RENEWAL ----------
function handleRenewalChange(id, value) {

    updateField(id, "renewal", value);

    const section = document.getElementById(`renewal-${id}`);
    section.style.display = value === 'Yes' ? 'contents' : 'none';
}

// ---------- REMOVE ----------
function removeItem(id) {

    const state = getState();

    state.s4 = state.s4.filter(i => i.id !== id);
    saveState(state);

    document.getElementById(`card-s4-${id}`)?.remove();
}