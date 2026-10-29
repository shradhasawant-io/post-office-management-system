
'use strict';

const form = document.querySelector('#forgot-form');
const message = document.querySelector('#message');
const button = document.querySelector('#submit-btn');

const GENERIC_MESSAGE =
    'If the supplied username and recovery email match an active account, ' +
    'a password reset link will be sent. Check your inbox and spam folder.';

function showMessage(text, type = 'info') {
    message.textContent = text;
    message.dataset.type = type;
    message.hidden = false;
}

form.addEventListener('submit', async (event) => {
    event.preventDefault();

    message.hidden = true;
    button.disabled = true;

    try {
        const response = await fetch('/api/auth/forgot-password', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username: document.querySelector('#username').value.trim(),
                email: document.querySelector('#email').value.trim()
            })
        });

        if (!response.ok) {
            // Do not display backend internals or account-specific information.
            showMessage(
                'The request could not be processed right now. Please try again later.',
                'error'
            );
            return;
        }

        // HTTP 200 means the API accepted the request.
        // It does NOT prove that the asynchronous email was delivered.
        showMessage(GENERIC_MESSAGE, 'info');

    } catch (error) {
        // A network error means the browser could not reach the API.
        // Do not describe this as an email-delivery failure.
        showMessage(
            'Cannot connect to the application. Check that Spring Boot is running and try again.',
            'error'
        );
    } finally {
        button.disabled = false;
    }
});
