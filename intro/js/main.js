/* ============================================================
   Agent4j — Official Website | v2.0
   Interactive Functions
   ============================================================ */

(function () {
    'use strict';

    // ---------- Nav scroll effect ----------
    const nav = document.querySelector('.nav');
    let lastScroll = 0;

    window.addEventListener('scroll', () => {
        const scrollY = window.scrollY;
        nav.classList.toggle('scrolled', scrollY > 40);
        lastScroll = scrollY;
    }, {passive: true});

    // ---------- Mobile nav toggle ----------
    const navToggle = document.querySelector('.nav-toggle');
    const navLinks = document.querySelector('.nav-links');

    if (navToggle) {
        navToggle.addEventListener('click', () => {
            navLinks.classList.toggle('open');
        });

        // Close nav on link click
        document.querySelectorAll('.nav-links a').forEach(link => {
            link.addEventListener('click', () => {
                navLinks.classList.remove('open');
            });
        });
    }

    // ---------- IntersectionObserver for reveal animations ----------
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add('visible');
            }
        });
    }, {
        threshold: 0.1,
        rootMargin: '0px 0px -40px 0px'
    });

    document.querySelectorAll('.reveal').forEach(el => observer.observe(el));

    // ---------- Copy code blocks ----------
    function copyCode(btn, codeId) {
        const codeEl = document.getElementById(codeId);
        if (!codeEl) return;

        const code = codeEl.innerText;
        navigator.clipboard.writeText(code).then(() => {
            btn.textContent = '已复制 ✓';
            btn.classList.add('copied');
            setTimeout(() => {
                btn.textContent = '复制';
                btn.classList.remove('copied');
            }, 2000);
        }).catch(() => {
            // Fallback
            const textarea = document.createElement('textarea');
            textarea.value = code;
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand('copy');
            document.body.removeChild(textarea);
            btn.textContent = '已复制 ✓';
            btn.classList.add('copied');
            setTimeout(() => {
                btn.textContent = '复制';
                btn.classList.remove('copied');
            }, 2000);
        });
    }

    // Expose copyCode globally
    window.copyCode = copyCode;

    // ---------- Smooth scroll for anchor links ----------
    document.querySelectorAll('a[href^="#"]').forEach(link => {
        link.addEventListener('click', (e) => {
            const targetId = link.getAttribute('href');
            if (targetId === '#') return;

            const target = document.querySelector(targetId);
            if (target) {
                e.preventDefault();
                const navHeight = nav ? nav.offsetHeight : 70;
                const targetPos = target.getBoundingClientRect().top + window.scrollY - navHeight;

                window.scrollTo({
                    top: targetPos,
                    behavior: 'smooth'
                });
            }
        });
    });

    // ---------- Terminal cursor blinking ----------
    // Already handled by CSS

    // ---------- Stat counter animation ----------
    function animateCounters() {
        document.querySelectorAll('.stat-value[data-target]').forEach(el => {
            const target = parseInt(el.dataset.target);
            const duration = 1500;
            const start = performance.now();

            function update(currentTime) {
                const elapsed = currentTime - start;
                const progress = Math.min(elapsed / duration, 1);
                const eased = 1 - Math.pow(1 - progress, 3);
                const current = Math.floor(eased * target);

                if (el.dataset.suffix) {
                    el.textContent = current + el.dataset.suffix;
                } else {
                    el.textContent = current + '%';
                }

                if (progress < 1) {
                    requestAnimationFrame(update);
                }
            }

            requestAnimationFrame(update);
        });
    }

    // Trigger counter animation when section is visible
    const counterObserver = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                animateCounters();
                counterObserver.unobserve(entry.target);
            }
        });
    }, {threshold: 0.3});

    document.querySelectorAll('.cache-stats').forEach(el => {
        counterObserver.observe(el);
    });

    // ---------- Scroll progress indicator ----------
    const scrollProgress = document.createElement('div');
    scrollProgress.style.cssText = `
    position: fixed;
    top: 0;
    left: 0;
    width: 0;
    height: 2px;
    background: linear-gradient(90deg, var(--neon), var(--amber));
    z-index: 101;
    transition: width 0.1s linear;
  `;
    document.body.appendChild(scrollProgress);

    window.addEventListener('scroll', () => {
        const docHeight = document.documentElement.scrollHeight - window.innerHeight;
        const scrollPercent = (window.scrollY / docHeight) * 100;
        scrollProgress.style.width = scrollPercent + '%';
    }, {passive: true});

    // ---------- Dynamic year in footer ----------
    document.querySelectorAll('[data-year]').forEach(el => {
        el.textContent = new Date().getFullYear();
    });

    // ---------- Particle effect on hero (optional) ----------
    // Simple starfield effect
    const hero = document.querySelector('.hero');
    if (hero && window.innerWidth > 768) {
        const canvas = document.createElement('canvas');
        canvas.style.cssText = `
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      pointer-events: none;
      z-index: 0;
    `;
        canvas.width = hero.offsetWidth;
        canvas.height = hero.offsetHeight;
        hero.appendChild(canvas);

        const ctx = canvas.getContext('2d');
        const stars = Array.from({length: 80}, () => ({
            x: Math.random() * canvas.width,
            y: Math.random() * canvas.height,
            size: Math.random() * 2 + 0.5,
            alpha: Math.random() * 0.5 + 0.2,
            speed: Math.random() * 0.02 + 0.005
        }));

        function drawStars() {
            ctx.clearRect(0, 0, canvas.width, canvas.height);

            stars.forEach(star => {
                ctx.beginPath();
                ctx.arc(star.x, star.y, star.size, 0, Math.PI * 2);
                ctx.fillStyle = `rgba(0, 230, 118, ${star.alpha})`;
                ctx.fill();

                star.y -= star.speed;
                if (star.y < -10) {
                    star.y = canvas.height + 10;
                    star.x = Math.random() * canvas.width;
                }
            });

            requestAnimationFrame(drawStars);
        }

        drawStars();

        // Resize handler
        window.addEventListener('resize', () => {
            canvas.width = hero.offsetWidth;
            canvas.height = hero.offsetHeight;
        });
    }

    // ---------- Console welcome ----------
    console.log('%c Agent4j %c 纯 Java 的 AI 编码代理 ',
        'background:#00e676;color:#000;padding:4px 8px;border-radius:4px 0 0 4px;font-weight:bold;font-size:14px;font-family:monospace;',
        'background:#141c24;color:#e8edf2;padding:4px 8px;border-radius:0 4px 4px 0;font-size:14px;font-family:monospace;'
    );
    console.log('https://github.com/ezdemo/agent4j');
})();
