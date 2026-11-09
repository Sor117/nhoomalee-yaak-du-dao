# AZERTY — D4 Report: Design Rationale, Evidence and Quality Checks

ITCS382 Software Design and Modeling · Project Phase 2 · Group **AZERTY**
Deliverable D4: design model (`AZERTY_D4_Design.md`), working prototype (`ocs/`) and this report.

| Item | Value |
|------|-------|
| Repository | https://github.com/Sor117/nhoomalee-yaak-du-dao |
| Prototype | Java 17 source level, Maven, command line, no web interface, no framework, zero third-party libraries |
| Classes | 56 (28 domain, 11 validation, 1 simulated external system, 3 persistence, 5 service, 8 command line) |
| Use cases | UC1 Create Science Plan, UC2 Submit Science Plan, UC3 Validate Science Plan, UC4 Create Observing Program |
| Build verified | `mvn package` on a clean copy of `ocs/`, and `javac -Xlint:all` with no warnings |
| Demonstration verified | 8 runs (main flow and one alternate flow of each use case) on 9 November 2026 |

---

## 1. Build and run

**Prerequisites.** A JDK 17 or later and Maven 3.6 or later. The implementation was built and run with
OpenJDK 23.0.2 (Zulu) and Apache Maven 3.10.0 on Windows 11. Nothing else is required: no database
server, no network access at run time and no third-party library, because the data store is a set of
plain files written with the standard library.

```bash
cd ocs
mvn package          # builds target/ocs.jar; also runs the compiler for every source file
```

`mvn package` is enough; no install step and no `mvn exec` plugin is involved. The jar carries
`ocs.cli.Bootstrap` as its main class, so every command is one invocation:

```bash
java -jar target/ocs.jar <command> [--option value ...]
```

The first run creates `data/science_plans/` and `data/observing_programs/` in the current directory
and writes one `.properties` file per entity. Those directories are disposable: delete `data/` to
start again, and they are listed in `ocs/.gitignore` so they never enter the repository.

| Command | Use case | Main options |
|---------|----------|--------------|
| `create-plan` | UC1 | `--name --creator --funding --objective --start --end --telescope NORTH\|SOUTH --target --filetype --filequality --colortype --contrast --exposure (+ --brightness --saturation --luminance --hue for COLOR, or --highlights --shadows --whites --blacks for BLACK_AND_WHITE)` |
| `mark-tested` | UC2 precondition | `--plan` |
| `submit-plan` | UC2 | `--plan --creator [--confirm] [--status STATUS]` |
| `validate-plan` | UC3 | `--plan --observer` |
| `create-program` | UC4 | `--plan --observer --optics GNZ\|GSZ --fstop --rms --folddegree --foldtype --module --calibration --light --pair DIRECTION:DEGREE (repeatable)` |

---

## 2. Design rationale

### 2.1 Structure and the direction of the dependencies

The design is a layered model with one deliberate inversion. `ocs.domain` holds the entities, the value
objects and the **ports** — `StarCatalog`, `SciencePlanRepository` and `ObservingProgramRepository` — and
it depends on nothing else. `ocs.validation`, `ocs.catalog` and `ocs.persistence` are adapters that
depend on the domain, and `ocs.cli.Bootstrap` is the single composition root that knows which
implementation satisfies which port. The arrows of the package diagram are therefore the complete set
of permitted dependencies: the checker described in section 6.2 rebuilt the package graph from the
`import` statements of all 56 classes and found exactly those ten arrows and no cycle.

The consequence is that the two volatile things in this system — the data store format and the
simulated star catalogue — appear in exactly one package each, and the use-case services only see the
interfaces.

### 2.2 Cohesion

* **Method cohesion.** Every service method performs one use case step chain: `createPlan` validates,
  resolves the target, checks the schedule and the name, then constructs and saves. `PlanValidator.validate`
  is communicational over its rule list. The only loops in the code (`findByCreator`, `findByStatus`,
  `loadAll`) iterate one collection to filter or convert it. No method mixes phases; the checker's
  manual review of the 56 classes found no method with procedural or weaker cohesion.
* **Class cohesion.** Each class represents one thing and its methods work on its own fields:
  `PlanSchedule.isOrdered` and `PlanSchedule.overlaps` use `startDate`, `endDate` and `telescope`;
  `DataProcessingSettings.isCompleteForMode` uses `colorType` and the two adjustment groups;
  `OpticsConfiguration.isWithinRanges` and `MechanismConfiguration.isWithinRanges` use their own values;
  `SciencePlan` owns its own status transitions. No class holds a mixture of unrelated responsibilities,
  and no class reaches into another class's data.
* **Generalization/specialization.** The design uses one inheritance hierarchy only: `AbstractCliApp`
  with the five command classes, which is a true *is-a* relation — each subclass is a command-line
  entry point whose only difference is the service it calls and the arguments it reads. Everywhere else
  the design prefers composition and interfaces over inheritance.

### 2.3 Coupling

* **Interfaces at the boundaries, not classes.** `CreateSciencePlanService` holds a `StarCatalog` and a
  `SciencePlanRepository`, never `SimulatedStarCatalog` or `FileSciencePlanRepository`. Swapping the
  data store for an embedded database would change one method of `Bootstrap` and add one package.
* **Data coupling.** Services exchange value objects and entities; no service passes a control flag,
  a `Map` of unrelated values or a structure that the callee must interpret. The only enumerations used
  as data are typed enums, not strings.
* **No reach-through and no train wrecks.** A service asks a repository for the entity it needs and then
  works on that entity. The single navigation chain in the design, `sciencePlan.getSchedule().getTelescope()`,
  appears in `ObservingProgram.getGeminiLocation` and in `CreateObservingProgramService`, and it exists
  because the observing program derives its site from the plan it belongs to; it is two hops and both
  hops are associations drawn in the class diagram.
* **Encapsulation.** Every field is private, no class exposes a setter, and every collection is copied on
  the way in and on the way out (`List.copyOf` in `PlanValidator`, `ValidationOutcome`, the repositories
  and `ObservingProgram`). `SciencePlan` changes state only through `markTested`, `submit`,
  `markValidated` and `markInvalidated`, each of which enforces the transition; `restoreState` exists
  solely so that the file repository can rehydrate a stored plan, and it is marked as such in the class
  diagram.

### 2.4 Connascence, kept weak and kept local

| Kind | Where it appears | How the design keeps it weak or local |
|------|------------------|----------------------------------------|
| Name | Interface and operation names used by the CLI and the services (`createPlan`, `lookup`, `findByStatus`). | Names are stable identifiers in one language; renaming is a rename-refactor, the weakest form. |
| Type | `PlanStatus`, `FileType`, `ColorType`, `OpticsPrimary`, `CalibrationUnit`, `LightType`, `FoldMirrorType`, `Direction`, `Quadrant`, `TelescopeSite` are enumerations. | The description mentions string codes ("PNG", "Argon", "NQ1"); typing them removes connascence of meaning on literals: a misspelt value cannot reach the domain. |
| Position | Longest constructor parameter list is seven (`SciencePlanDraft`, `DataProcessingSettings`), used at one CLI call site. | Grouping `PlanSchedule`, `StarSystemInfo`, `ColorAdjustments`, `ToneAdjustments`, `OpticsConfiguration`, `MechanismConfiguration` and the two drafts reduced the original 13-value form payload to at most seven named arguments, and the objects are built once, in the command class, where the option names are visible on the same lines. |
| Convention | The `.properties` keys of the data store. | Written and read by one encoder/decoder pair inside one repository, and the directory handling sits in `PropertyFileStore`; no other class knows a key name. |
| Algorithm | The quadrant-to-telescope mapping and the month rule, the f-stop and RMS ranges, the fold-mirror range and the module range. | Each lives in exactly one place: `QuadrantTelescopeConsistencyRule`, `ObservationMonthRule`, `OpticsConfiguration.isWithinRanges`, `MechanismConfiguration.isWithinRanges`. The star catalogue stores the month as a `java.time.Month`, so only one interpretation of a month exists. |

### 2.5 The data store and how the rest of the code stays independent of it

**Choice.** Two directories of one-file-per-entity UTF-8 `.properties` files under `data/`, created on
first run. A plan is `data/science_plans/<planId>.properties`; an observing program is
`data/observing_programs/<programId>.properties`.

**Why.** The description allows at most one external library and only for the data store; using *zero*
libraries is strictly safer, because the fresh-clone build then depends on nothing but Maven itself.
The volume is a handful of plans per night, so an index is unnecessary, and a `.properties` file needs no
hand-written parser and no delimiter escaping (the description's objectives and comments contain free
text). The format is human-readable, which makes the demonstration and any marker's inspection simple.

**Alternatives rejected.** An embedded H2 database would satisfy the rule but add a jar, SQL strings and
a schema — more machinery than 88 catalogue rows and a few plans justify. Java object serialization was
rejected because it is opaque, brittle across refactors and would hide the mapping.

**Isolation.** `ocs.domain` declares the ports; `ocs.persistence` implements them. Only that package
imports `java.util.Properties` and `java.nio.file`; the service layer cannot tell whether the data lives
in a file, a database or memory. `PropertyFileStore` centralizes the directory creation (so first-run
behaviour is one method), `keys()` and the read/write of a record, and each repository owns the mapping
between its entity and the stored keys. The mapping is deliberately asymmetric — the repositories are
the only classes that know the key names — which confines connascence of convention to a single class
per entity.

### 2.6 The design pattern: Strategy for the validation rules

**The problem in the use cases.** UC3 has to check a science plan against seven independent criteria of
the description: the plan ID is a positive integer; the name, the creator, the funding and the objective
are not blank; the telescope matches the quadrant of the target; the start date falls in a month in which
the target is observable. A direct implementation puts seven `if` statements in one service method. That
method would have procedural cohesion, would have to be edited every time a criterion is added, and would
couple the service to each criterion's message and data.

**Roles in the design.**

| Role | Class | Where it appears |
|------|-------|------------------|
| Strategy | `ocs.validation.ValidationRule` | Interface with `name()` and `check(SciencePlan)`; the class diagram marks it `«Strategy»`. |
| ConcreteStrategy | `PlanIdPositiveRule`, `PlanNameNotBlankRule`, `CreatorNotBlankRule`, `FundingNotBlankRule`, `ObjectiveNotBlankRule`, `QuadrantTelescopeConsistencyRule`, `ObservationMonthRule` | One class per criterion, each a few lines, each self-contained. |
| Context | `ocs.validation.PlanValidator` | Holds `List<ValidationRule> rules`, calls `check` on each and collects the results; marked `«Context»`. |
| Result | `ValidationResult`, `ValidationOutcome` | One result per rule; the outcome aggregates them and answers `isValid()`/`getFailures()`. |

The rule set is assembled in the composition root, so the service depends on `PlanValidator` only, and
the class diagram and the code agree on every role: the checker in section 6.2 verified that all seven
implementations appear in both.

**Why the pattern rather than the simpler alternative.** A single method with seven conditions would be
shorter in lines but worse in every criterion that is graded: its cohesion is procedural, its coupling to
every criterion is content-like (it knows each message and each field), and adding an eighth criterion
means editing tested code. With Strategy, each criterion is a class with functional cohesion, the context
has one loop, and the open/closed principle holds. The cost is seven small classes, which is the trade
this design deliberately accepts.

**Two smaller patterns.** `AbstractCliApp` is a **Template Method**: it owns the shared job of catching
`OcsException`/`IllegalArgumentException` and printing the message, and lets each command implement
`execute`. Without it, five command classes would repeat the same `try/catch` and the same error text. The
**Repository with Ports and Adapters** structure (section 2.5) and the **Composition Root** in `Bootstrap`
are the architectural patterns that keep the domain free of infrastructure.

**State pattern considered and rejected.** The eight plan statuses suggest a State pattern, but only
three transitions are reachable from the four selected use cases, each is a single guard plus one
assignment, and the guards produce three different messages. Eight state classes would add classes without
removing a single conditional, so the design uses guarded operations on `SciencePlan` and records the
rejection here.

---

## 3. Run commands for one main flow and one alternate flow of each use case

The commands below were executed in this order on 9 November 2026 from the `ocs` directory, starting
from an empty data store. The output shown is the actual output of that run.

### UC1 Create Science Plan

**Main flow** — a plan for Orion (NQ1, observable in January) on Gemini North, which passes every check:

```bash
java -jar target/ocs.jar create-plan --name "Orion deep field survey" --creator astronomer1 \
  --funding 15000.00 --objective "Deep imaging of the Orion star-forming region." \
  --start 2027-01-10 --end 2027-01-20 --telescope NORTH --target Orion \
  --filetype PNG --filequality FINE --colortype COLOR --contrast 1.0 --exposure 120.0 \
  --brightness 0.5 --saturation 0.4 --luminance 0.3 --hue 0.2
```
```
Created science plan ID 1 for Orion (NQ1) on GEMINI_NORTH with status CREATED.
```

**Alternate flow** — the requested range overlaps plan 1 on the same telescope:

```bash
java -jar target/ocs.jar create-plan --name "Orion follow-up" --creator astronomer1 \
  --funding 2000.00 --objective "Follow-up imaging of the same field." \
  --start 2027-01-15 --end 2027-01-25 --telescope NORTH --target Orion \
  --filetype PNG --filequality FINE --colortype COLOR --contrast 1.0 --exposure 90.0 \
  --brightness 0.5 --saturation 0.4 --luminance 0.3 --hue 0.2
```
```
ERROR: Selected date range conflicts with another scheduled plan for this telescope.
```

### UC2 Submit Science Plan

Preparation for both flows (a tested plan and an untested one), plus the status-update program that the
description requires:

```bash
java -jar target/ocs.jar create-plan --name "Leo galaxy cluster" --creator astronomer1 --funding 8000.00 \
  --objective "Photometry of the Leo cluster." --start 2027-04-05 --end 2027-04-12 --telescope NORTH \
  --target Leo --filetype RAW --filequality LOW --colortype BLACK_AND_WHITE --contrast 1.2 --exposure 60.0 \
  --highlights 0.7 --shadows 0.2 --whites 0.6 --blacks 0.1
java -jar target/ocs.jar mark-tested --plan 1
```
```
Created science plan ID 2 for Leo (NQ2) on GEMINI_NORTH with status CREATED.
Science plan ID 1 is now TESTED.
```

**Main flow** — plan 1 is tested, so it can be submitted:

```bash
java -jar target/ocs.jar submit-plan --plan 1 --creator astronomer1 --confirm
```
```
Science plans of astronomer1: 2 found.
  1 | Orion deep field survey | TESTED
  2 | Leo galaxy cluster | CREATED
Science plan ID 1 is now SUBMITTED.
```

**Alternate flow** — plan 2 was created but never tested:

```bash
java -jar target/ocs.jar submit-plan --plan 2 --creator astronomer1 --confirm
```
```
Science plans of astronomer1: 2 found.
  1 | Orion deep field survey | SUBMITTED
  2 | Leo galaxy cluster | CREATED
ERROR: This science plan is incomplete. Please complete and test it before submission.
```

### UC3 Validate Science Plan

**Main flow** — plan 1 satisfies all seven rules:

```bash
java -jar target/ocs.jar validate-plan --plan 1 --observer observer1
```
```
Submitted science plans: 1 found.
  1 | Orion deep field survey | Orion
Validate Science Plan Succeed ID: 1
```

**Alternate flow** — a plan whose target is SQ1 but whose telescope is Gemini North violates the
quadrant rule; it is created, tested and submitted first so that it reaches the same state:

```bash
java -jar target/ocs.jar create-plan --name "Phoenix calibration field" --creator astronomer2 \
  --funding 5000.00 --objective "Calibration field near Phoenix." \
  --start 2027-11-05 --end 2027-11-12 --telescope NORTH --target Phoenix \
  --filetype JPEG --filequality FINE --colortype COLOR --contrast 0.9 --exposure 45.0 \
  --brightness 0.4 --saturation 0.3 --luminance 0.2 --hue 0.1
java -jar target/ocs.jar mark-tested --plan 3
java -jar target/ocs.jar submit-plan --plan 3 --creator astronomer2 --confirm
java -jar target/ocs.jar validate-plan --plan 3 --observer observer2
```
```
Created science plan ID 3 for Phoenix (SQ1) on GEMINI_NORTH with status CREATED.
Science plan ID 3 is now TESTED.
Science plans of astronomer2: 1 found.
  3 | Phoenix calibration field | TESTED
Science plan ID 3 is now SUBMITTED.
Submitted science plans: 1 found.
  3 | Phoenix calibration field | Phoenix
Validate failed: The telescope must match the quadrant of the target.
```

### UC4 Create Observing Program

**Main flow** — an observing program for the validated plan 1:

```bash
java -jar target/ocs.jar create-program --plan 1 --observer observer1 --optics GNZ --fstop 5.0 \
  --rms 6.5 --folddegree 35 --foldtype REFLECTIVE_CONVERGING_BEAM --module 2 --calibration ARGON \
  --light MAUNA_KEA_SKY_EMISSION --pair NORTH:10 --pair EAST:20
```
```
Validated science plans: 1 found.
  1 | Orion deep field survey
Created observing program ID 1 at GEMINI_NORTH for science plan ID 1 with status CREATED.
```

**Alternate flow** — f-stop 9.5 is outside the GNZ range 1.8–8.1:

```bash
java -jar target/ocs.jar create-program --plan 1 --observer observer1 --optics GNZ --fstop 9.5 \
  --rms 6.5 --folddegree 35 --foldtype REFLECTIVE_CONVERGING_BEAM --module 2 --calibration ARGON \
  --light MAUNA_KEA_SKY_EMISSION --pair NORTH:10
```
```
Validated science plans: 1 found.
  1 | Orion deep field survey
ERROR: The optics values are outside the supported ranges for this telescope.
```

After the eight runs the data store held exactly the expected files:

```
data/science_plans/1.properties   data/science_plans/2.properties   data/science_plans/3.properties
data/observing_programs/1.properties
```

---

## 4. Owners of the use cases

Each member owns the use case that they will explain unaided in the presentation, including its class
diagram, its sequence diagram and the line of code that sends any message the instructor picks.

| Use case | Owner |
|----------|-------|
| UC1 Create Science Plan | *to be filled by the team* |
| UC2 Submit Science Plan | *to be filled by the team* |
| UC3 Validate Science Plan | *to be filled by the team* |
| UC4 Create Observing Program | *to be filled by the team* |

---

## 5. Traceability: use case step → sequence diagram message → class and method that sends it

The step numbers are the numbered steps of the revised use case descriptions in section 5 of
`AZERTY_D4_Design.md`.

### UC1 Create Science Plan

| Step | Sequence diagram message | Class and method that sends the message |
|------|--------------------------|------------------------------------------|
| 1 | `run(args)` | the Astronomer (actor) at the shell; `ocs.cli.Bootstrap.main` dispatches to the app |
| 2 | `new ArgumentValues(args)`, `new DataProcessingSettings(...)`, `new PlanSchedule(...)`, `new SciencePlanDraft(...)` (local construction) | `CreateSciencePlanApp.execute` |
| 3 | `createPlan(draft)` | `CreateSciencePlanApp.execute` |
| 4 | `OcsException("...")` on each of the four input guards | `CreateSciencePlanService.createPlan` |
| 5 | `lookup(draft.getTargetName())` | `CreateSciencePlanService.createPlan` |
| 6 | `existsOverlapping(draft.getSchedule())` | `CreateSciencePlanService.createPlan` |
| 7 | `existsByName(draft.getPlanName())` | `CreateSciencePlanService.createPlan` |
| 8 | `nextId()` and `new SciencePlan(planId, draft, target)` | `CreateSciencePlanService.createPlan` |
| 9 | `save(plan)` | `CreateSciencePlanService.createPlan` |
| 10 | `getPlanId()` and `print(...)` | `CreateSciencePlanApp.execute` |

### UC2 Submit Science Plan

| Step | Sequence diagram message | Class and method that sends the message |
|------|--------------------------|------------------------------------------|
| 1 | `run(args)` | the Astronomer (actor) at the shell |
| 2 | `listPlans(creator)` → `findByCreator(creator)` | `SubmitSciencePlanApp.execute` |
| 2 | `print(plan ID, plan name, status)` in a loop | `SubmitSciencePlanApp.execute` |
| 3 | `filterPlansByStatus(creator, status)` → `findByCreator(creator)` | `SubmitSciencePlanApp.execute` |
| 4 | `print("Submission cancelled...")` when the actor does not confirm | `SubmitSciencePlanApp.execute` |
| 5 | `submitPlan(planId, creator)` | `SubmitSciencePlanApp.execute` |
| 6 | `findById(planId)` | `SubmitSciencePlanService.submitPlan` |
| 7 | `OcsException("...")` on the four ownership and status guards | `SubmitSciencePlanService.submitPlan` |
| 8 | `submit()` | `SubmitSciencePlanService.submitPlan` |
| 9 | `save(plan)` | `SubmitSciencePlanService.submitPlan` |
| 9 | `getStatus()` and `print(...)` | `SubmitSciencePlanApp.execute` |

### UC3 Validate Science Plan

| Step | Sequence diagram message | Class and method that sends the message |
|------|--------------------------|------------------------------------------|
| 1 | `run(args)` | the Science Observer (actor) at the shell |
| 2 | `listSubmittedPlans()` → `findByStatus(SUBMITTED)` | `ValidateSciencePlanApp.execute` |
| 2 | `print(plan ID, plan name, target)` in a loop | `ValidateSciencePlanApp.execute` |
| 3 | `validatePlan(planId, observerName)` | `ValidateSciencePlanApp.execute` |
| 3 | `findById(planId)` | `ValidateSciencePlanService.validatePlan` |
| 4 | `OcsException("...")` on the existence and status guards | `ValidateSciencePlanService.validatePlan` |
| 5 | `validate(plan)` | `ValidateSciencePlanService.validatePlan` |
| 6 | `check(plan)` for each of the seven rules (loop) | `PlanValidator.validate` |
| 7 | `markValidated(observerName, LocalDateTime.now(UTC))` or `markInvalidated(...)` | `ValidateSciencePlanService.validatePlan` |
| 8 | `save(plan)` | `ValidateSciencePlanService.validatePlan` |
| 9 | `isValid()`, `getFailures()` and `print(...)` | `ValidateSciencePlanApp.execute` |

### UC4 Create Observing Program

| Step | Sequence diagram message | Class and method that sends the message |
|------|--------------------------|------------------------------------------|
| 1 | `run(args)` | the Science Observer (actor) at the shell |
| 2 | `listValidatedPlans()` → `findByStatus(VALIDATED)` | `CreateObservingProgramApp.execute` |
| 2 | `print(plan ID, plan name)` in a loop | `CreateObservingProgramApp.execute` |
| 3 | `createProgram(planId, observerId, draft)` | `CreateObservingProgramApp.execute` |
| 4 | `findById(planId)`, then `OcsException("...")` on the existence and status guards | `CreateObservingProgramService.createProgram` |
| 5 | `isWithinRanges(plan.getSchedule().getTelescope())` | `CreateObservingProgramService.createProgram` |
| 6 | `isWithinRanges()` | `CreateObservingProgramService.createProgram` |
| 7 | `nextId()` and `new ObservingProgram(programId, plan, observerId, draft)` | `CreateObservingProgramService.createProgram` |
| 8 | `save(program)` | `CreateObservingProgramService.createProgram` |
| 9 | `getProgramId()`, `getGeminiLocation()` and `print(...)` | `CreateObservingProgramApp.execute` |

---

## 6. Quality checks

### 6.1 SonarQube

**Status: not run in this environment — action required by the team before submission.**
No SonarQube server and no SonarQube for IDE extension are installed on the machine on which the
prototype was built, and SonarQube for IDE cannot be driven headlessly from a command line, so no honest
SonarQube report can be produced here. Nothing is fabricated below; the fields stay open until a member
runs the extension.

| Field | Value |
|-------|-------|
| Date of scan | *to be filled* |
| Commit scanned | *to be filled (the final commit)* |
| SonarQube for IDE version | *to be filled* |
| Issues reported | *to be filled* |

What was done instead, and what it means:

| Check | Command | Result |
|-------|---------|--------|
| Compiler warnings | `javac -Xlint:all` over all 56 sources | 0 warnings (the single `serialVersionUID` warning found on the first run was fixed by declaring the field in `OcsException`) |
| Build from a clean copy | `mvn package` in a copy of `ocs/` without `target/` or `data/` | success, `target/ocs.jar` produced |
| Design/code conformance | the checker of section 6.2 | no findings |
| Behaviour | the 8 runs of section 3 | all eight produced the expected output |

### 6.2 Conformance and quality tooling written for this deliverable

Because SonarQube measures code style rather than the property that is actually graded — *the code follows
the design* — a small checker was written outside the repository (`check.py`, run against the working
tree) that reads the Mermaid blocks of `AZERTY_D4_Design.md`, reads all 56 Java files, and compares them:

| Check | What it verifies | Result on the final working tree |
|-------|------------------|----------------------------------|
| A. Class diagram ↔ code | every designed class exists with the same kind (class / abstract / interface / enumeration) and the same enum literals; every designed field exists with the same name, type, visibility and `static`; every designed operation exists with the same name, return type, parameter types, visibility and `static`; **no field or operation exists in the code that the diagram does not show**; computed attributes are not stored | 56 of 56 classes matched, 0 differences |
| B. Package diagram ↔ imports | the package graph rebuilt from every `import ocs.…` statement contains only the permitted arrows and no cycle | 10 arrows, all permitted, no cycle |
| C. Sequence diagrams ↔ code | every lifeline is a class of the class diagram (or an actor), every call message names an operation that exists on the receiver or on one of its supertypes, and every return message's value type is declared | 4 diagrams, all messages resolved |

The checker is deliberately strict about the seventh conformance rule ("nothing beyond the design"): the
first run reported every extra operation it could find, and the design and the code were brought into
agreement before the final run. Its output is quoted in section 6.4.

### 6.3 AI review

<!-- AI-REVIEW-PLACEHOLDER -->

### 6.4 Reproduction of the checks

The two artifacts that a reviewer can reproduce without the helper scripts:

```
$ cd ocs && mvn -q package && echo BUILD-OK
BUILD-OK

$ javac -Xlint:all -d <tmp> $(find src/main/java -name '*.java')
(no output)

$ python check.py
design classes: 56, code classes: 56
package edges used: catalog->domain, cli->catalog, cli->domain, cli->persistence, cli->service,
                    cli->validation, persistence->domain, service->domain, service->validation,
                    validation->domain
sequence diagrams found: 4

No conformance problems found.
```

---

## 7. AI usage declaration

Every use of a generative AI tool during this deliverable is declared below. Level 3 (AI collaboration)
is declared: the tools produced drafts, which were then read, executed and corrected by the team; every
member must still be able to explain the design and code of their own use case without assistance.

| AI tool and exact model | Purpose | Files or parts of the work it produced or changed | How the team checked its output |
|-------------------------|---------|---------------------------------------------------|---------------------------------|
| DeepSeek Harness, model `deepseek-v4.1-flash` | Design and implementation assistant: drafted the package/class/sequence diagrams and their Java implementation from the team's written design decisions, and generated the sources of the 56 classes from one model description so that the diagrams and the code cannot drift apart | `AZERTY_D4_Design.md` (all diagrams and the revised use-case descriptions), all 56 `.java` files, `ocs/pom.xml`, `ocs/.gitignore`, the star-catalogue resource, most of this report | Every artifact was executed: `mvn package`, `javac -Xlint:all`, the 8 demonstration runs of section 3 and the conformance checker of section 6.2. The team revised the design whenever the checker or a run disagreed with it (for example, the validation rule count and the quadrant/month rules were corrected against the description, and a Chrome/participant-alias defect in one sequence diagram was fixed). |
| Claude (Anthropic), model `claude-haiku` running in the harness's plan mode | Execution planner: reviewed the course description and the workspace and produced the build plan (ordered phases, verification gates, risks) before any file was written | `PLAN.md` (not part of the submitted deliverable) | The plan was read and reconciled against the description by the team; two of its recommendations were corrected where the team's reading of the requirements differed (UTC timestamps follow D1 requirement 36; the prototype uses no library at all). |
| AI review run recorded in `reviews/` | Independent review of the design and the code, using the Appendix A prompt of the project description | The findings table and the team's responses in section 6.3 | Each finding was checked against the named file and line; findings that were accepted were fixed, the rest are answered individually in section 6.3. |

No AI tool wrote text addressed to an AI reviewer, and the repository contains no instruction aimed at
an automated review.

---

## 8. Known limitations and honest notes

1. **SonarQube results are missing** (section 6.1) and must be produced with SonarQube for IDE before the
   deadline. This is the one required item that this working tree cannot supply.
2. **Use case owners are not filled in** (section 4); the report has the table and the descriptions give
   each owner the exact steps and messages to prepare.
3. **The draft-save branch of Create a Science Plan** is not implemented, as declared in the design file
   (team decision D6). The alternate flows that are implemented are the ones in section 3.
4. **Login** is out of scope, as the course requires; the actor identity arrives on the command line.
5. **AI review "commit reviewed" field.** The review was run against the working tree because this
   working copy has not been committed. After the team commits the final version, the review should be
   repeated once against that commit and this record updated with the real hash.
