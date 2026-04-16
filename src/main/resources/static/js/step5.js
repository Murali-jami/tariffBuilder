window.addEventListener('DOMContentLoaded', () => {
    // Restore previously entered values
    const state = getState();
    if (state.price) document.getElementById('price').value = state.price;
    if (state.endDate) document.getElementById('endDate').value = state.endDate;
    if (state.publicityCode) document.getElementById('publicityCode').value = state.publicityCode;
    if (state.isCorporate) document.getElementById('isCorporate').checked = state.isCorporate;
});

// Each input writes to state immediately on change
function onPriceChange(val) {
    const state = getState();
    state.price = val;
    saveState(state);
}

function onEndDateChange(val) {
    const state = getState();
    state.endDate = val;
    saveState(state);
}

function onPublicityCodeChange(val) {
    const state = getState();
    state.publicityCode = val;
    saveState(state);
}

function onCorporateChange(checked) {
    const state = getState();
    state.isCorporate = checked;
    saveState(state);
}