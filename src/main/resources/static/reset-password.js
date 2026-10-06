const token = new URLSearchParams(location.hash.slice(1)).get('token');
history.replaceState(null, '', location.pathname);

const form = document.getElementById('reset-form');
const statusText = document.getElementById('status');
const button = document.getElementById('submit');

if (!token) {
  statusText.textContent = 'Parola sıfırlama bağlantısı eksik veya geçersiz.';
  form.hidden = true;
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const password = document.getElementById('password').value;
  const confirmation = document.getElementById('confirmation').value;
  if (password !== confirmation) {
    statusText.textContent = 'Parolalar eşleşmiyor.';
    return;
  }

  button.disabled = true;
  try {
    const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!csrfResponse.ok) throw new Error('CSRF');
    const csrf = await csrfResponse.json();
    const response = await fetch('/api/auth/reset-brand-password', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
      body: JSON.stringify({ token, password })
    });
    if (!response.ok) {
      const problem = await response.json().catch(() => null);
      statusText.textContent = problem?.detail || 'Parola değiştirilemedi. Bağlantı geçersiz veya süresi dolmuş olabilir.';
      return;
    }
    form.reset();
    form.hidden = true;
    statusText.textContent = 'Parolanız değiştirildi. Yeni parolanızla giriş yapabilirsiniz.';
  } catch {
    statusText.textContent = 'Bağlantı kurulamadı. Lütfen tekrar deneyin.';
  } finally {
    button.disabled = false;
  }
});
