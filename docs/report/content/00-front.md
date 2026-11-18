---
title: "Quell: Intelligent IT Service Management and Incident Resolution System"
author: "Yatharth Soni"
description: "Project report, Group 94, CS F213 / MAC F212 Object-Oriented Programming, Semester I 2026-2027"
lang: en
---

```{=html}
<div class="cover">
  <img class="logo" src="../brand/quell-logo-white.svg" alt="Quell">
  <svg class="ring" viewBox="0 0 24 22" aria-hidden="true">
    <circle cx="11" cy="11" r="9.25" fill="none" stroke="#151515" stroke-width="3.5"/>
    <rect x="11" y="18.5" width="13" height="3.5" fill="#151515"/>
  </svg>
  <div class="title-block">
    <p class="kicker">Project report &nbsp;·&nbsp; Object-Oriented Programming</p>
    <h1 class="cover-title">Intelligent IT Service Management and Incident Resolution System</h1>
    <p class="subtitle">Quell takes an IT incident from the first report to its closure: it prioritises, assigns, tracks and escalates on its own, explains every decision, and learns from every incident it resolves.</p>
  </div>
  <div class="meta">
    <span class="k">Group</span><span class="v">94</span>
    <span class="k">Author</span><span class="v">Yatharth Soni &nbsp;·&nbsp; 2025ADPS1461H</span>
    <span class="k">Course</span><span class="v">CS F213 / MAC F212 &nbsp;·&nbsp; Semester I, 2026–2027</span>
    <span class="rule"></span>
    <span class="k">Product</span><span class="v">Quell 1.2.0</span>
    <span class="k">Source</span><span class="v">github.com/PresidentOfBirTawil/itsm</span>
    <span class="k">Date</span><span class="v">November 2026</span>
  </div>
</div>
```

# Quell at a glance {#glance .front kicker="At a glance"}

Quell is an IT service management product. An employee reports a problem in seconds, on a form, by chatting with an assistant or by voice. Quell then sets its priority, assigns the right technician, watches the deadline, escalates what nobody picks up and keeps everyone informed, and it learns from every incident it resolves.

```{=html}
<div class="stats">
  <div><div class="value">617</div><div class="label">automated tests,<br>all passing</div></div>
  <div><div class="value">15<small>&thinsp;/&thinsp;16</small></div><div class="label">innovation challenges<br>of the brief built in</div></div>
  <div><div class="value">41</div><div class="label">functional requirements,<br>each traced to its class</div></div>
  <div><div class="value">222</div><div class="label">Java classes in 28 packages,<br>16,700 lines</div></div>
</div>
```

:::: {.glance-grid}
::: {.left}
### Where each deliverable is

| Deliverable of the brief                                                             | Section | Page |
|----------------------------------------------------------------------|--------|------|
| 1 · Problem analysis | 1 | <span data-page-of="#problem"></span> |
| 2 · Requirements specification | 2 · A | <span data-page-of="#requirements"></span> |
| 3 · System architecture | 3 | <span data-page-of="#architecture"></span> |
| 4 · UML diagrams | 4 · C | <span data-page-of="#uml"></span> |
| 5 · Technology stack, nine questions | 7 | <span data-page-of="#technology"></span> |
| 6 · Source code | 12 · E | <span data-page-of="#demo"></span> |
| 7 · Database design | 8 | <span data-page-of="#database"></span> |
| 8 · Testing | 9 · B | <span data-page-of="#testing"></span> |
| 9 · Project demonstration | 12 | <span data-page-of="#demo"></span> |
| OOP concepts, design patterns | 5 | <span data-page-of="#oop"></span> |
| Intelligent features, innovation | 6 | <span data-page-of="#intelligence"></span> |
| Exception handling | 5.5 | <span data-page-of="#exceptions"></span> |
| Security | 10 | <span data-page-of="#security"></span> |
:::

::: {.right}
### Three workflows, shown in the video

```{=html}
<ol class="flows">
  <li><b>Report → prioritise → assign → notify</b>A payment outage reported by chat becomes P1 (3 × 3 × 3 = 27), gets its deadlines and a skilled technician, who is alerted live.</li>
  <li><b>Investigate → SLA → resolve → notify</b>The technician acknowledges, asks a question (the SLA clock pauses), applies a suggested fix; the employee confirms and rates.</li>
  <li><b>Duplicates → correlate → escalate</b>Similar reports are linked, people join the known problem, two failing services are traced to the network they share, and an ignored P1 is escalated.</li>
</ol>
```

### Try it

```{=html}
<dl class="facts">
  <dt>Download</dt><dd>github.com/PresidentOfBirTawil/itsm/<wbr>releases/latest<br><span class="muted">Windows, macOS, Linux; Java included</span></dd>
  <dt>Source</dt><dd>github.com/PresidentOfBirTawil/itsm</dd>
  <dt>Sign in</dt><dd><code>admin@itsm.local</code> / <code>admin12345</code></dd>
  <dt>Video</dt><dd>94_OOP_S1_2025-2026_Project</dd>
</dl>
```
:::
::::

# Contents {#contents .front kicker="Contents"}

```{=html}
<div class="toc"></div>
```
