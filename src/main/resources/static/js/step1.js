// Show or hide the Commercial card based on billing type
function applyCommercialVisibility(billingType) {
    const subTypeSection = document.getElementById('subTypeSection');
    if (!subTypeSection) return;

    if (billingType === 'POSTPAID') {
        subTypeSection.style.opacity = '0.3';
        subTypeSection.style.pointerEvents = 'none';
        sessionStorage.removeItem('pkgSubType');
        document.querySelectorAll('#subTypeSection .type-card').forEach(c =>
            c.classList.remove('selected')
        );
    } else {
        subTypeSection.style.opacity = '1';
        subTypeSection.style.pointerEvents = 'auto';
    }
}

// Restore selections when page loads
document.addEventListener('DOMContentLoaded', function () {
    const savedType = sessionStorage.getItem('pkgType');
    const savedSubType = sessionStorage.getItem('pkgSubType');

    // Restore Billing Type
    if (savedType) {
        const typeCard = document.getElementById('card-' + savedType);
        if (typeCard) {
            // Mark as selected
            document.querySelectorAll('#typeSection .type-card').forEach(c =>
                c.classList.remove('selected')
            );
            typeCard.classList.add('selected');

            // Unlock category section
            const subGroup = document.getElementById('subTypeGroup');
            subGroup.style.opacity = '1';
            subGroup.style.pointerEvents = 'auto';

            // Apply Commercial visibility based on restored billing type
            applyCommercialVisibility(savedType);
        }
    }

    // Restore Category (only if billing type was selected)
    // If saved subtype was COMMERCIAL but billing is now PREPAID, clear it
    if (savedSubType && savedType) {
        if (savedSubType === 'COMMERCIAL' && savedType === 'PREPAID') {
            sessionStorage.removeItem('pkgSubType');
        } else {
            const subCard = document.getElementById('card-' + savedSubType);
            if (subCard) {
                document.querySelectorAll('#subTypeSection .type-card').forEach(c =>
                    c.classList.remove('selected')
                );
                subCard.classList.add('selected');
            }
        }
    }
});

// Existing functions (slightly improved)
function selectType(type) {
    const currentType = sessionStorage.getItem('pkgType');
    const subGroup = document.getElementById('subTypeGroup');

    // If same type clicked again → deselect everything
    if (currentType === type) {
        sessionStorage.removeItem('pkgType');
        sessionStorage.removeItem('pkgSubType');

        document.querySelectorAll('#typeSection .type-card').forEach(c =>
            c.classList.remove('selected')
        );

        document.querySelectorAll('#subTypeSection .type-card').forEach(c =>
            c.classList.remove('selected')
        );

        // Lock category section again
        subGroup.style.opacity = '0.3';
        subGroup.style.pointerEvents = 'none';

        // Reset Commercial visibility
        applyCommercialVisibility(null);

        return;
    }

    // Normal selection
    sessionStorage.setItem('pkgType', type);

    document.querySelectorAll('#typeSection .type-card').forEach(c =>
        c.classList.remove('selected')
    );

    document.getElementById('card-' + type).classList.add('selected');

    // Unlock category section
    subGroup.style.opacity = '1';
    subGroup.style.pointerEvents = 'auto';

    // Reset category when billing type changes
    document.querySelectorAll('#subTypeSection .type-card').forEach(c =>
        c.classList.remove('selected')
    );
    sessionStorage.removeItem('pkgSubType');

    // Show/hide Commercial based on billing type
    applyCommercialVisibility(type);

    // If switching to PREPAID and COMMERCIAL was previously selected, clear it
    if (type === 'PREPAID' && sessionStorage.getItem('pkgSubType') === 'COMMERCIAL') {
        sessionStorage.removeItem('pkgSubType');
    }
}

function selectSubType(subType) {
    const currentSubType = sessionStorage.getItem('pkgSubType');

    // If same category clicked again → deselect it
    if (currentSubType === subType) {
        sessionStorage.removeItem('pkgSubType');

        document.querySelectorAll('#subTypeSection .type-card').forEach(c =>
            c.classList.remove('selected')
        );

        return;
    }

    // Normal selection
    sessionStorage.setItem('pkgSubType', subType);

    document.querySelectorAll('#subTypeSection .type-card').forEach(c =>
        c.classList.remove('selected')
    );

    document.getElementById('card-' + subType).classList.add('selected');
}


// document.addEventListener('DOMContentLoaded', function () {

//     if (NETWORK_ID) {
//         const currentChargeId = sessionStorage.getItem("periodicChargeID");
//         loadPeriodicCharges(NETWORK_ID, currentChargeId);
//     } else {
//         console.error("NETWORK_ID not available");
//     }

// });

// function selectPeriodicCharge(chargeId) {

//     if (!chargeId) {
//         sessionStorage.removeItem("periodicCharge");
//         return;
//     }

//     sessionStorage.setItem("periodicChargeID", chargeId);

//     console.log("Selected Periodic Charge:", chargeId);
// }

// async function loadPeriodicCharges(networkId, currentChargeId) {
//     try {
//         const url = currentChargeId
//             ? `/periodic_charges/${networkId}?currentChargeId=${encodeURIComponent(currentChargeId)}`
//             : `/periodic_charges/${networkId}`;

//         const response = await fetch(url);

//         if (!response.ok) {
//             throw new Error("Failed to fetch periodic charges");
//         }

//         const charges = await response.json();

//         const dropdown = document.getElementById("periodicCharge");

//         dropdown.innerHTML =
//             '<option value="">────── Select ──────</option>';

//         charges.forEach(charge => {
//             const option = document.createElement("option");

//             option.value = charge.CHARGE_ID;

//             option.textContent = charge.CHARGE_DESC || charge.CHARGE_ID;

//             dropdown.appendChild(option);
//         });

//         // Restore previously selected value
//         const savedCharge =
//             sessionStorage.getItem("periodicChargeID");

//         if (savedCharge) {
//             dropdown.value = savedCharge;
//         }

//     } catch (error) {
//         console.error("Error loading periodic charges:", error);
//     }
// }