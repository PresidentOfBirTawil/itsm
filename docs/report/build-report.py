#!/usr/bin/env python3
"""Assembles docs/report/Quell-Project-Report.md from the parts and the earlier report notes."""
import re, pathlib

here = pathlib.Path(__file__).parent
notes = (here / 'parts' / 'notes-source.md').read_text()
part = lambda name: (here / 'parts' / name).read_text().rstrip() + '\n'

def between(start, end):
    i = notes.index(start)
    j = notes.index(end, i + len(start))
    return notes[i:j].rstrip() + '\n'

def table_at(heading):
    i = notes.index(heading)
    j = notes.index('\n|', i) + 1
    k = notes.index('\n\n', j)
    return notes[j:k] + '\n'

def promote(text):
    """### headings become ## (sections taken from the notes sat one level deeper)."""
    return re.sub(r'(?m)^### ', '## ', text)

def fix(text, replacements):
    for a, b in replacements:
        assert a in text, a[:80]
        text = text.replace(a, b)
    return text

out = []
out.append(part('00-cover.md'))
out.append(part('01-summary.md'))

# 1. Problem analysis
problem = between('### The problem', '### Proposed solution')
out.append('# 1. Problem analysis\n\n' + promote(problem + '\n' + part('02-solution-assumptions.md')))

# 2. Requirements
roles = table_at('### User roles')
roles = fix(roles, [
    ('| **Employee** | Register. Report an incident. See their own incidents and their timelines.',
     '| **Employee** | Register. Report an incident on the form or by chatting with the assistant (also by voice), with screenshots. Follow a known problem ("I\'m affected too"). Check the service status page. Search and rate knowledge articles. See their own incidents and their timelines.'),
    ('Rate the resolution. Read their notifications. |', 'Rate the resolution. Read their notifications and desktop alerts. Make API keys. |'),
    ('Correct the classification of their own incidents. Search all incidents and open them read-only. |',
     'Correct the classification of their own incidents. Use suggested solutions; write knowledge articles. Attach files. Search all incidents and open them read-only. |'),
    ('Manage users, technicians (skills, maximum workload, availability), services, categories and SLA policies.',
     'Manage users, technicians (skills, maximum workload, availability), services and their dependencies, categories and SLA policies. Connect webhooks.'),
])
fr = table_at('### Functional requirements').rstrip('\n') + '\n' + part('03-fr-extra.md')
out.append('# 2. Requirements specification\n\n## 2.1 User roles\n\n' + roles + '\n## 2.2 Functional requirements\n\n' + fr
           + '\n## 2.3 Non-functional requirements\n\n' + part('04-nfr.md'))

# 3. Business rules
br = table_at('## 3. Business rules').rstrip('\n') + '\n' + part('05-br-extra.md')
out.append('# 3. Business rules\n\n' + br)

out.append(part('06-architecture.md'))
out.append(part('07-uml.md'))

# 6. Object-oriented design
oop = table_at('## 5. OOP concepts')
oop = '\n'.join(line for line in oop.split('\n') if not line.startswith('| **Generics and collections**')).rstrip('\n') + '\n' + part('08-oop-extra.md')
patterns = table_at('## 6. Design patterns').rstrip('\n') + '\n' + part('08-patterns-extra.md')
left_out = between('Two patterns that were considered and left out', '\n---\n')
out.append('# 6. Object-oriented design\n\n## 6.1 OOP concepts\n\n' + oop + '\nThe class diagrams in section 5 show all of these.\n\n## 6.2 Design patterns\n\n'
           + patterns + '\n' + left_out)

# 7. Priority
priority = between('### The rule', '\n---\n')
priority = fix(priority, [
    ('- The number of affected users is part of the impact level, not a separate input (see Assumptions).',
     '- The number of people affected raises the impact automatically: 3 people make it at least Medium, 5 make it High (BR-23).'),
])
out.append('# 7. The priority algorithm\n\n' + promote(priority))

out.append(part('09-intelligence.md'))

# 9. Exceptions
exc = between('## 9. Exception handling', '\n---\n')
exc = exc.replace('## 9. Exception handling', '# 9. Exception handling')
k = exc.index('\n\nOne class, `ErrorHandler`')
exc = exc[:k] + '\n' + part('10-exceptions-extra.md').rstrip('\n') + exc[k:]
exc = exc.rstrip('\n') + ' The REST API has its own handler, `ApiExceptionHandler`, which answers in JSON.\n'
out.append(exc)

out.append(part('11-security.md'))

# 11. Technology
questions = between('### 1. Why did you choose this programming language?', '## 12. How the system would scale')
questions = questions.replace('\n---\n', '\n')
questions = fix(questions, [
    ('Security is simpler too,\nbecause the server decides what is on the page (a button a user may not press is never sent) and\nthere is no public API to protect separately.',
     'Security is simpler too,\nbecause the server decides what is on the page (a button a user may not press is never sent);\nprograms get their own REST API with its own rules (section 8.5).'),
    ('A small amount of plain JavaScript is used only where it helps: about a hundred lines for\nlive updates (section 8), which replace parts of a page with HTML the server rendered. Pages still work\nwith JavaScript switched off.',
     'Plain JavaScript, without a framework, is used only where it helps: nine small files add live updates,\nthe chat, voice input, drag-and-drop uploads, the command palette and the installable app. Pages still\nwork with JavaScript switched off.'),
    ('and the data still survives a restart because it is stored in a file. Tests use a fresh in-memory H2,\nso they are fast and independent. Because all access goes through JPA, moving to PostgreSQL or MySQL\nis a change of driver and connection settings, not of code.',
     'and the data still survives a restart because it is stored in a file; the desktop installers use it\ntoo. Tests use a fresh in-memory H2, so they are fast and independent. Because all access goes through\nJPA, server installations run the same code on **PostgreSQL** (the `prod` profile and docker-compose),\nwhich we verified end to end: only the driver and connection settings differ.'),
    ('| Database | PostgreSQL / MySQL | The right choice for production (section 12). Needs an installed server or Docker, which makes the project harder to run for an evaluator. |',
     '| Database | PostgreSQL / MySQL | PostgreSQL is used for server installations (prod profile, Docker). H2 stays the default so the desktop installer and an evaluator need nothing installed. |'),
    ('- Excellent testability: 498 tests run in under a minute with no external services.',
     '- Excellent testability: 617 tests run in about four minutes with no external services.'),
    ('- H2 is not meant for many simultaneous users or large data; a production system needs a database\n  server.',
     '- H2 is not meant for many simultaneous users or large data; server installations use PostgreSQL.'),
    ('- Server-rendered pages reload on each action. Live updates soften this for dashboards and lists, but\n  highly interactive features (drag and drop, offline use) would need more client-side code.',
     '- Server-rendered pages reload on each action. Live updates, the chat and drag-and-drop uploads soften\n  this with small scripts, but a rich editor or offline editing would need more client-side code.'),
    ('asynchronous notifications. No change to the domain model or the patterns is needed. The full answer\nis the next section.',
     'asynchronous notifications. No change to the domain model or the patterns is needed. The full answer\nis section 11.3.'),
])
scale = between('Today: one process, one embedded database', '\n---\n')
scale = fix(scale, [
    ('Move to **PostgreSQL**. Only the driver and connection settings change, because all access is through JPA.',
     '**Done:** the `prod` profile runs on **PostgreSQL**; only the driver and connection settings change, because all access is through JPA. Next:'),
    ('Write notifications to an **outbox table** in the same transaction and deliver them from a **message queue** (RabbitMQ or Kafka) with retries.',
     '**Done for webhooks:** they use an **outbox table** written in the same transaction and are delivered with retries. Next: the same for notifications, delivered from a **message queue** (RabbitMQ or Kafka).'),
    ('| 10 | Operations. | Container image, health checks and metrics (Spring Boot Actuator), central logging, automatic deployment. | Medium |',
     '| 10 | Operations. | **Done:** container image, docker-compose, CI building and testing every change. Next: health checks and metrics (Spring Boot Actuator), central logging, automatic deployment. | Medium |'),
])
out.append(part('12-stack.md') + '\n## 11.2 The nine questions of the brief\n\n' + questions.replace('### ', '### ')
           + '\n## 11.3 How the system would scale\n\n' + scale)

out.append(part('13-database.md'))
out.append(part('14-testing.md'))
out.append(part('15-ui.md'))
out.append(part('16-demo.md'))
out.append(part('17-team.md'))
out.append(part('18-limits.md'))
out.append(part('19-appendix.md'))

report = '\n'.join(out)
report = report.replace('I-ITSM', 'Quell')
# Links to diagrams in the old notes pointed to diagrams/…; the report lives one level deeper.
report = re.sub(r'\]\(diagrams/', '](../diagrams/', report)
(here / 'Quell-Project-Report.md').write_text(report)
print(len(report.splitlines()), 'lines')
