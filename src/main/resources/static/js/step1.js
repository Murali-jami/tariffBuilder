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