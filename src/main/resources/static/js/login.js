function setRole() {
    let toggle = document.getElementById("roleToggle");
    let roleField = document.getElementById("roleField");
    let roleText = document.getElementById("roleText");
    let networkField = document.getElementById("networkField");

    let adminError = document.getElementById("adminError");
    let userError = document.getElementById("userError");

    if (toggle.checked) {

        roleField.value = "ADMIN";
        roleText.innerText = "Admin";
        networkField.style.display = "none";

        // show only admin error
        if (adminError) adminError.style.display = "block";
        if (userError) userError.style.display = "none";

    } else {

        roleField.value = "USER";
        roleText.innerText = "User";
        networkField.style.display = "block";

        // show only user error
        if (adminError) adminError.style.display = "none";
        if (userError) userError.style.display = "block";
    }
}

window.onload = function () {
    sessionStorage.clear();
    setRole();
}

function clearForm() {
    document.querySelectorAll("input[type='text'], input[type='password']")
        .forEach(input => input.value = "");

    // optional: clear error messages
    let adminError = document.getElementById("adminError");
    let userError = document.getElementById("userError");

    if (adminError) adminError.remove();
    if (userError) userError.remove();
}