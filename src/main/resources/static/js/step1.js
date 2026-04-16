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
        }
    }

    // Restore Category (only if billing type was selected)
    if (savedSubType && savedType) {
        const subCard = document.getElementById('card-' + savedSubType);
        if (subCard) {
            document.querySelectorAll('#subTypeSection .type-card').forEach(c =>
                c.classList.remove('selected')
            );
            subCard.classList.add('selected');
        }
    }
});

// Existing functions (slightly improved)
function selectType(type) {
    sessionStorage.setItem('pkgType', type);

    // Clear previous selection
    document.querySelectorAll('#typeSection .type-card').forEach(c =>
        c.classList.remove('selected')
    );
    document.getElementById('card-' + type).classList.add('selected');

    // Unlock category group
    const subGroup = document.getElementById('subTypeGroup');
    subGroup.style.opacity = '1';
    subGroup.style.pointerEvents = 'auto';

    // Reset subtype when changing billing type
    document.querySelectorAll('#subTypeSection .type-card').forEach(c =>
        c.classList.remove('selected')
    );
    sessionStorage.removeItem('pkgSubType');
}

function selectSubType(subType) {
    sessionStorage.setItem('pkgSubType', subType);

    document.querySelectorAll('#subTypeSection .type-card').forEach(c =>
        c.classList.remove('selected')
    );
    document.getElementById('card-' + subType).classList.add('selected');

    setTimeout(() => {
        window.isInternalNavigation = true;
        window.location.href = "/builder/step2";
    }, 300);
}