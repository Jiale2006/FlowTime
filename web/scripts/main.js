/* ============================================================
   入场编排 · IntersectionObserver + 错峰延迟
   只动 opacity / transform，降级由 base.css 的 reduced-motion 处理
   ============================================================ */
(() => {
  "use strict";

  const tiles = Array.from(document.querySelectorAll(".reveal"));
  if (!tiles.length) return;

  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reduce) {
    document.documentElement.classList.remove("js");   // 撤销隐藏，内容直接可见
    tiles.forEach((el) => el.classList.add("in"));
    return;
  }

  /* 按分组错峰：同组内相邻元素间隔 80ms，组间隔 200ms */
  let groupPrev = null;
  let withinGroup = 0;
  tiles.forEach((el) => {
    const sec = el.closest("section");
    if (sec !== groupPrev) {
      groupPrev = sec;
      withinGroup = 0;
    }
    el.style.transitionDelay = `${withinGroup * 80}ms`;
    withinGroup = (withinGroup + 1) % 4;
  });

  const io = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          e.target.classList.add("in");
          io.unobserve(e.target);
        }
      });
    },
    { rootMargin: "0px 0px -12% 0px", threshold: 0.08 }
  );

  tiles.forEach((el) => io.observe(el));

  /* 首屏版头即刻入场（不等滚动） */
  requestAnimationFrame(() => {
    document.querySelectorAll(".masthead .reveal").forEach((el, i) => {
      el.style.transitionDelay = `${i * 90}ms`;
      el.classList.add("in");
    });
  });
})();
