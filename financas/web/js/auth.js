/* Página de login/cadastro */
(() => {
  const $ = (id) => document.getElementById(id);

  const tabLogin = $('tab-login');
  const tabRegister = $('tab-register');
  const formLogin = $('form-login');
  const formRegister = $('form-register');
  const errorBox = $('auth-error');
  const errorMsg = $('auth-error-msg');

  function showError(msg) {
    errorMsg.textContent = msg;
    errorBox.hidden = false;
  }

  function clearError() {
    errorBox.hidden = true;
  }

  function selectTab(which) {
    const isLogin = which === 'login';
    tabLogin.classList.toggle('active', isLogin);
    tabRegister.classList.toggle('active', !isLogin);
    formLogin.hidden = !isLogin;
    formRegister.hidden = isLogin;
    clearError();
  }

  tabLogin.addEventListener('click', () => selectTab('login'));
  tabRegister.addEventListener('click', () => selectTab('register'));

  // Mostrar / ocultar senha
  document.querySelectorAll('[data-toggle-password]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const input = $(btn.dataset.togglePassword);
      if (!input) return;
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      btn.querySelector('.icon-eye').hidden = show;
      btn.querySelector('.icon-eye-off').hidden = !show;
    });
  });

  async function post(url, payload) {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(payload),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.error || 'Não foi possível concluir a operação.');
    return data;
  }

  formLogin.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearError();
    const btn = formLogin.querySelector('button[type=submit]');
    btn.disabled = true;
    try {
      await post('/api/auth/login', {
        email: $('login-email').value,
        password: $('login-password').value,
      });
      location.href = '/dashboard.html';
    } catch (err) {
      showError(err.message);
      btn.disabled = false;
    }
  });

  formRegister.addEventListener('submit', async (e) => {
    e.preventDefault();
    clearError();
    const btn = formRegister.querySelector('button[type=submit]');
    btn.disabled = true;
    try {
      await post('/api/auth/register', {
        name: $('reg-name').value,
        email: $('reg-email').value,
        password: $('reg-password').value,
      });
      location.href = '/dashboard.html';
    } catch (err) {
      showError(err.message);
      btn.disabled = false;
    }
  });
})();
