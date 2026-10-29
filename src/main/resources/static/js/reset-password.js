'use strict';
const form = document.querySelector('#reset-form');
const message = document.querySelector('#message');
const button = document.querySelector('#submit-btn');
const token = new URLSearchParams(window.location.search).get('token');

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  message.hidden = true;
  const newPassword = document.querySelector('#new-password').value;
  const confirmPassword = document.querySelector('#confirm-password').value;
  if (!token) {
    message.textContent = 'The password reset link is invalid or has expired.';
    message.hidden = false;
    return;
  }
  if (newPassword !== confirmPassword) {
    message.textContent = 'New password and confirmation do not match.';
    message.hidden = false;
    return;
  }
  button.disabled = true;
  try {
    const response = await fetch('/api/auth/reset-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, newPassword, confirmPassword })
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || 'The password reset link is invalid or has expired.');
    message.textContent = data.message || 'Your password has been reset. Please sign in with your new password.';
    form.querySelectorAll('input').forEach((input) => input.disabled = true);
    button.hidden = true;
    // Remove the token from the address bar after success.
    window.history.replaceState({}, document.title, window.location.pathname);
  } catch (error) {
    message.textContent = error.message || 'Unable to reset password. Request a new link.';
  } finally {
    message.hidden = false;
    button.disabled = false;
  }
});
