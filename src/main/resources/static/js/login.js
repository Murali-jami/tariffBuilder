function clearForm() {

    // clear all input fields
    document.querySelectorAll("input[type='text'], input[type='password']")
        .forEach(input => input.value = "");

    // clear error message
    const error = document.querySelector(".error-message");
    if (error) {
        error.style.display = "none";
        error.innerText = "";
    }
}

/* 🔥 runs after page load */
function clearOnLoad() {

    const error = document.querySelector(".error-message");

    // if error exists OR always clear (recommended)
    document.querySelectorAll("input[type='text'], input[type='password']")
        .forEach(input => input.value = "");

    if (error) {
        error.style.display = "none";
        error.innerText = "";
    }
}



/* 🔥 handles refresh/back cache (VERY IMPORTANT) */
window.addEventListener("pageshow", function (event) {

    // 🔥 only clear on refresh / back navigation
    if (event.persisted || performance.getEntriesByType("navigation")[0].type === "reload") {

        document.querySelectorAll("input[type='text'], input[type='password']")
            .forEach(input => input.value = "");

        const error = document.querySelector(".error-message");
        if (error) {
            error.style.display = "none";
            error.innerText = "";
        }
    }

    // Always reset the submit button — covers the case where the user
    // hit Submit, then navigated back via the browser (bfcache can
    // restore the page mid-spinner otherwise).
    resetLoginButton();
});

function resetLoginButton() {
    const btn = document.getElementById("loginSubmitBtn");
    const spinner = document.getElementById("loginSubmitSpinner");
    const label = document.getElementById("loginSubmitLabel");
    if (!btn) return;

    btn.disabled = false;
    if (spinner) spinner.style.display = "none";
    if (label) label.textContent = "Login";
}

document.addEventListener("DOMContentLoaded", function () {
    const form = document.querySelector(".login-shell form");
    if (!form) return;

    form.addEventListener("submit", function () {
        const btn = document.getElementById("loginSubmitBtn");
        const spinner = document.getElementById("loginSubmitSpinner");
        const label = document.getElementById("loginSubmitLabel");
        if (!btn) return;

        btn.disabled = true;
        if (spinner) spinner.style.display = "inline-block";
        if (label) label.textContent = "Logging in…";
        // No preventDefault — this is a normal form POST; the button
        // stays disabled until the page navigates away (success) or
        // reloads with an error message (handled by pageshow above).
    });
});

window.onload = function () {
    sessionStorage.clear();
}