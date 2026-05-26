window.addEventListener('DOMContentLoaded', () => {
    // Restore previously entered values
    const state = getState();
    if (state.price) document.getElementById('price').value = state.price;
    if (state.endDate) document.getElementById('endDate').value = state.endDate;
    if (state.publicityCode) document.getElementById('publicityCode').value = state.publicityCode;
    if (state.isCorporate) document.getElementById('isCorporate').checked = state.isCorporate;

    // ── Clone mode: swap Save Config → Clone Package ──────────────
    if (sessionStorage.getItem('cloneMode') === 'true') {
        const saveBtn = document.getElementById('saveConfigBtn');
        const cloneBtn = document.getElementById('clonePackageBtn');
        if (saveBtn) saveBtn.style.display = 'none';
        if (cloneBtn) cloneBtn.style.display = 'inline-flex';
    }
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

// ── Clone Package — called from the Clone Package button in step5 ──
async function clonePackageFromBuilder() {
    const state = getState();

    if (!state?.s2?.length) {
        alert('Service Plan selection in Step 2 is required');
        return;
    }
    if (!state.price) {
        alert('Enter charge amount');
        return;
    }
    if (!state.endDate) {
        alert('Select end date');
        return;
    }
    if (!state.publicityCode) {
        alert('Enter publicity code');
        return;
    }

    // Retrieve the original payload stored when Modify was clicked
    const rawPayload = sessionStorage.getItem('clonePayload');
    if (!rawPayload) {
        alert('Clone payload missing. Please go back to the Clone page and try again.');
        return;
    }

    let payload;
    try {
        payload = JSON.parse(rawPayload);
    } catch (e) {
        alert('Invalid clone payload. Please try again.');
        return;
    }

    function formatDateToMMDDYYYY(dateStr) {
        if (!dateStr) return '12/31/2030';
        const [year, month, day] = dateStr.split('-');
        return `${month}/${day}/${year}`;
    }

    const chargeId = (payload.tpName || payload.data?.tariffPackageDesc || '') + '_PR';

    // Merge builder's current step5 values into the payload's data block
    payload.data = {
        ...payload.data,
        charge: state.price,
        endDate: formatDateToMMDDYYYY(state.endDate),
        publicityId: state.publicityCode,
        isCorporateYn: state.isCorporate || false,
        chargeId: chargeId,
        packageType: sessionStorage.getItem('pkgType') || payload.data?.packageType,
        tariffPackCategory: sessionStorage.getItem('pkgSubType') || payload.data?.tariffPackCategory || 'NORMAL',
        tariffPlanId: Number(state.s2[0].id),
        tariffPlanName: state.s2[0].name,
        defaultAtps: (state.s3 || []).map(item => ({
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
        allowedAtps: (state.s4 || []).map(item => ({
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

    const cloneBtn = document.getElementById('clonePackageBtn');
    if (cloneBtn) { cloneBtn.disabled = true; cloneBtn.textContent = 'Cloning…'; }

    try {
        const res = await fetch('/clone', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const result = await res.json();

        if (!res.ok || result.error) {
            alert(result.error || 'Clone failed. Please try again.');
            return;
        }

        alert('✅ Cloned successfully!\nNew plan: ' + result.clonedTpName);

        // Clean up clone-specific session keys
        sessionStorage.removeItem('cloneMode');
        sessionStorage.removeItem('clonePayload');
        clearBuilderSession();

        window.isInternalNavigation = true;
        window.location.href = '/builder/step1';

    } catch (err) {
        console.error('Clone error:', err);
        alert('Server error during clone. Please try again.');
    } finally {
        if (cloneBtn) { cloneBtn.disabled = false; cloneBtn.textContent = 'CLONE PACKAGE'; }
    }
}