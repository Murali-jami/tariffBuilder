


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
});

window.onload = function () {
    sessionStorage.clear();
}

