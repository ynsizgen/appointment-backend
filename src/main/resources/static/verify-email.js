const token = new URLSearchParams(location.hash.slice(1)).get('token');
history.replaceState(null, '', location.pathname);
const statusText = document.getElementById('status');
const button = document.getElementById('verify');
if (!token) { statusText.textContent = 'Doğrulama bağlantısı eksik veya geçersiz.'; button.disabled = true; }
button.addEventListener('click', async () => {
  button.disabled = true;
  try {
    const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    if (!csrfResponse.ok) throw new Error();
    const csrf = await csrfResponse.json();
    const response = await fetch('/api/auth/verify-email', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
      body: JSON.stringify({ token })
    });
    if (!response.ok) {
      statusText.textContent = 'Doğrulama tamamlanamadı. Bağlantı geçersiz, kullanılmış veya süresi dolmuş olabilir.';
      button.disabled = false; return;
    }
    statusText.textContent = 'E-postanız doğrulandı. Hesabınızla giriş yapabilirsiniz; markanız admin aktivasyonunu bekliyor.';
  } catch {
    statusText.textContent = 'Bağlantı kurulamadı. Lütfen tekrar deneyin.'; button.disabled = false;
  }
});
