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
  `loadAll`) iterate one collection to filter or convert it.
* **Assessed level per method.** Method cohesion was graded method by method, not claimed in the
  aggregate: the domain operations, the seven rules and the services' query methods are functional;
  `FileSciencePlanRepository.save`/`findById` and `FileObservingProgramRepository.save`/`findById` are
  sequential (open, map, write; read, map, close); `filterPlansByStatus` and `ValidationOutcome.isValid`
  are communicational; `CreateSciencePlanService.createPlan`,
  `FileSciencePlanRepository.toPlan`/`toProperties` and `CreateSciencePlanApp.execute` are procedural —
  each is a fixed sequence over one aggregate, performed once at a boundary (a use case, the data-store
  format, the command line); the three value-object predicates (`isCompleteForMode`, the two
  `isWithinRanges`) are logical, because each combines the checks that the description applies
  together for the selected mode or site. The independent AI review of section 6.3 reached the same
  division and each of its findings is answered there.
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
* **Short navigation.** A service asks a repository for the entity it needs and then works on that
  entity. Three calls take one extra hop — `plan.getSchedule().getTelescope()`,
  `plan.getTarget().getQuadrant()` and `draft.getOptics().isWithinRanges(...)` — and each hop is an
  association drawn in the class diagram, so the navigation is visible rather than hidden. The team
  kept them instead of adding `telescope()`/`quadrant()` wrappers to `SciencePlan`, because such
  wrappers move the same knowledge into the entity and enlarge its public surface; the AI review
  raised this as finding 21 and section 6.3 records the decision.
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
| Algorithm | The quadrant-to-telescope mapping and the month rule, the f-stop and RMS ranges, the fold-mirror range and the module range. | Each lives in exactly one place: `QuadrantTelescopeConsistencyRule`, `ObservationMonthRule`, `OpticsConfiguration.isWithinRanges`, `MechanismConfiguration.isWithinRanges`. The star catalogue stores the month as a `java.time.Month`, so only one interpretation of a month exists. One knowledge is deliberately repeated: the rule that a colour plan carries the colour adjustments and a black-and-white plan carries the tone adjustments is asked three times — by the command line (which options to read), by `DataProcessingSettings.isCompleteForMode` (which group must be present) and by the repository (which keys to write) — and each question is different, so the comparison is one enum test rather than a shared table. The AI review reports it as finding 27 and section 6.3 records the decision. |

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
| Results (not a role of the pattern) | `ValidationResult`, `ValidationOutcome` | One result per rule; the outcome aggregates them and answers `isValid()`/`getFailures()`. |

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
the design* — a small checker is shipped with the deliverable as `tools/check_conformance.py`. It reads the
Mermaid blocks of `AZERTY_D4_Design.md`, reads all 56 Java files, and compares them:

| Check | What it verifies | Result on the final working tree |
|-------|------------------|----------------------------------|
| A. Class diagram ↔ code | every designed class exists with the same kind (class / abstract / interface / enumeration) and the same enum literals; every designed field exists with the same name, type, visibility and `static`; every designed operation exists with the same name, return type, parameter types, visibility and `static`; **no field or operation exists in the code that the diagram does not show**; computed attributes are not stored | 56 of 56 classes matched, 0 differences |
| B. Package diagram ↔ imports | the package graph rebuilt from every `import ocs.…` statement contains only the permitted arrows and no cycle | 10 arrows, all permitted, no cycle |
| C. Sequence diagrams ↔ code | every lifeline is a class of the class diagram (or an actor), every call message names an operation that exists on the receiver or on one of its supertypes, and every return message's value type is declared | 4 diagrams, all messages resolved |

The checker is deliberately strict about the seventh conformance rule ("nothing beyond the design"): the
first run reported every extra operation it could find, and the design and the code were brought into
agreement before the final run. Its output is quoted in section 6.4.

### 6.3 AI review

**Run 1 — 9 November 2026.**

| Field | Value |
|-------|-------|
| Date | 9 November 2026, Asia/Bangkok |
| AI tool | DeepSeek Harness review subagent; a fresh context that saw only the repository files, not this report's reasoning |
| Exact model | `opencode-go/deepseek-v4.1-flash` (DeepSeek V4.1 Flash) |
| Commit reviewed | the working tree before the first commit — identical to the files committed as `9473e4d`, `f45ae73` and `a5ea0e3` |
| How the AI accessed the files | it read them directly from disk (design file, all 56 Java sources, `pom.xml`, `star_systems.csv`, this report, `PLAN.md`) |
| Prompt | Appendix A of the Project Phase 2 description, `<groupname>` replaced by `AZERTY` |
| Complete output | `reviews/2026-11-09_deepseek-v4.1-flash_run1.md` |

The run checked 56 classes, 185 operations, 87 attributes, 104 sequence-diagram messages and 6 packages,
and reported **44 findings: 2 Must fix, 22 Should fix, 20 Note**. The two Must-fix findings were real
defects of the design file — the package diagram omitted the twelve domain enumerations and the two
validation result classes — and both were corrected in the design. Every finding is answered below with
the change that closed it or the reason for keeping the design as it is.

| No. | Severity | Finding (abbreviated) | Response |
|-----|----------|-----------------------|----------|
| 1 | Must fix | twelve domain enumerations appear in no package box | **Fixed** — all twelve added to the `ocs.domain` box |
| 2 | Must fix | `ValidationResult`/`ValidationOutcome` missing from the validation box | **Fixed** — both added |
| 3 | Should fix | UC1/UC4 read the created object outside the branch that creates it | **Fixed** — both diagrams redrawn; the getters are inside the creating branch |
| 4 | Should fix | print messages omit the getters the code calls | **Fixed** — `getTarget`, `getSchedule`/`getTelescope`, `getStatus`, `getSciencePlan`/`getPlanId` added; text matches the code |
| 5 | Should fix | construction steps of UC1 step 2 and UC4 step 3 appear as no message | **Fixed** — a `note over APP` documents the objects built from the options in both diagrams |
| 6 | Should fix | no association from the service to the optics/mechanism objects it messages | **Fixed** — the service now has dependencies on the draft and the two configurations, and the draft holds both |
| 7 | Note | lifelines name the adapters while senders hold the ports | **Accepted** — a lifeline is one object that exists at run time, so the adapter name is the accurate label; the port type held by the sender is drawn in the class diagram and noted in section 4 of the design file |
| 8 | Note | UC3 activation bar closed inside a branch | **Fixed** — `deactivate SVC` moved after the fragment |
| 9 | Should fix | `OcsException` superclass invisible in the class diagram | **Fixed** — a note states `extends java.lang.RuntimeException` |
| 10 | Should fix | nine reference attributes have no association | **Fixed** — all nine drawn with multiplicity and role name |
| 11 | Note | composition overstates ownership of the position pairs | **Fixed** — changed to an association, and the draft copies its list |
| 12 | Note | the status predicate of the UC2 filter is not drawn | **Fixed** — a note marks it |
| 13 | Should fix | `createPlan` is procedural | **Accepted** — the four guards are the input contract of this one use case; a separate draft validator would forward four one-line checks and duplicate the Strategy role used in UC3. Section 2.2 records the level |
| 14 | Should fix | the three value-object predicates are logical | **Accepted** — each expresses one rule of the description for the selected mode or site; splitting it would push the range tables into the callers, a tighter connascence |
| 15 | Should fix | `toProperties` is procedural | **Accepted** — one pass over one aggregate at the data-store boundary; splitting per sub-object adds four methods with one caller each and hides the order |
| 16 | Should fix | `toPlan` is logical and also rehydrates state | **Accepted** — the colour branch is the decision answered in 27; rehydration is answered in 22 |
| 17 | Note | `execute` is procedural | **Accepted** — command-line boundary code; a factory would add a class with one caller |
| 18 | Should fix | the repository has three responsibilities | **Accepted** — the mapping is the data store's convention and lives where that convention lives; a mapper class would move the same code behind an interface used once |
| 19 | Note | the program repository allocates IDs and resolves a plan | **Accepted** — both belong to the repository that owns the aggregate; resolving in the service would still need the plan repository |
| 20 | Note | the draft doubles as the rehydration DTO | **Accepted** — the entity's constructor already takes a draft; a second constructor would duplicate the field copying |
| 21 | Should fix | reach-through chains contradict the rationale | **Accepted** — the extra hops are drawn as associations and section 2.3 now states the three of them instead of claiming there is one |
| 22 | Should fix | content coupling: persistence sets state through public rehydration operations | **Accepted** — package-private is impossible because persistence is a separate Java package; a rehydration factory would be the same operation with another name |
| 23 | Note | control coupling: the site selects an internal range table | **Accepted** — the site is data that selects the method's own table; passing the bounds would make every caller know them |
| 24 | Should fix | stamp coupling: every rule receives the whole plan | **Accepted** — that parameter is what keeps the interface stable when a criterion changes; per-value parameters would couple the validator to each rule's needs |
| 25 | Note | adapters import 11–15 domain types | **Accepted** — an adapter maps a whole aggregate, so it must see its value objects; a mapper would not narrow the type set |
| 26 | Note | user-facing copy lives in the domain and the service | **Accepted** — the messages are the wording of the provided description, which the prototype must follow; error codes would add a table for no gain at this size |
| 27 | Should fix | the colour-mode rule appears in three packages | **Accepted** — the three sites ask three different questions (which options to read, which group must be present, which keys to write) about one enum value; see section 2.4 |
| 28 | Note | the next-ID and status-filter scans are implemented twice | **Accepted** — each repository owns its own ID space; moving the scan into the store would couple the store to integer identities |
| 29 | Should fix | the data-store convention spans three classes | **Accepted** — the pair encoding is produced by the command class and consumed only inside the persistence package; a codec class would be used by one method |
| 30 | Note | option-name literals repeat | **Accepted** — the names are documented in section 1 and appear once per command; a constants class would be a class outside the design |
| 31 | Note | enumeration constants are reached by string surgery | **Accepted** — the accepted spellings are part of the documented command interface |
| 32 | Should fix | `getTelePositionPairs` returns the internal list | **Fixed** — the constructor copies with `List.copyOf` and the getter returns that copy |
| 33 | Note | five operations are public but have no outside caller | **Fixed** — they are private in the code and in the class diagram |
| 34 | Note | the status guard is duplicated in the service and the entity | **Accepted** — the service produces the use-case message; the entity guard is the invariant and stays as the second line |
| 35 | Should fix | `markValidated`/`markInvalidated` enforce no transition | **Fixed** — both now require the plan to be `SUBMITTED` |
| 36 | Should fix | `«Strategy»`/`«Context»` are not in the class diagram and "Result" is not a role | **Fixed** — both stereotypes added; the row is relabelled |
| 37 | Note | the Context never calls `name()` | **Accepted** — `name()` is the strategy's identity and each rule uses it to build its own result |
| 38 | Should fix | section 6.3 is an HTML placeholder while section 7 claims a review exists | **Fixed** — this section replaces it |
| 39 | Should fix | `reviews/` is empty | **Fixed** — the complete run output is in `reviews/2026-11-09_deepseek-v4.1-flash_run1.md` |
| 40 | Note | `PLAN.md` holds instructions addressed to an AI | **Fixed** — `PLAN.md` is untracked and listed in `.gitignore`, so it is not in the submitted tree; section 7 says so |
| 41 | Note | the cohesion claim is contradicted by findings 13–17 | **Fixed** — section 2.2 now states the assessed level of every method named there |
| 42 | Should fix | the UC1 Step column does not follow the description | **Fixed** — the Step column was renumbered; the reviewer read the previous draft of the table |
| 43 | Note | the reproduction block runs a `check.py` that is not in the repository | **Fixed** — the checker is shipped as `tools/check_conformance.py` and section 6.4 shows the real command and output |
| 44 | Note | three unused imports | **Fixed** — the generator's import scan no longer matches method names, and the imports are gone |

No finding remains unanswered. The accepted ones are quality judgements on a deliberately small design;
in each case the suggested alternative would move the same knowledge to another class, add a class used
once, or widen a public surface. A second run against the final commit is planned once the team commits
the final version (section 8).

### 6.4 Reproduction of the checks

All three checks repeat from a fresh clone of the repository:

```bash
# 1. the prototype builds and produces the jar
cd ocs
mvn -q package
echo BUILD-OK

# 2. the compiler reports no warnings (Windows: use %TEMP%\lint\ and the file list of step 3)
javac -Xlint:all -d /tmp/lint $(git ls-files 'ocs/src/main/java/*.java')

# 3. the design and the code agree, from the repository root
cd ..
python tools/check_conformance.py
```

Their output on the committed tree:

```
BUILD-OK
(no javac output)

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

The submitted tree contains no text addressed to an AI reviewer. The one file that does contain
instructions about how to run this review is the team's planning file `PLAN.md`, which is not part of the
repository and is listed in `.gitignore`.

---

## 8. Known limitations and honest notes

1. **SonarQube results are missing** (section 6.1) and must be produced with SonarQube for IDE before the
   deadline. This is the one required item that this working tree cannot supply.
2. **Use case owners are not filled in** (section 4); the report has the table and the descriptions give
   each owner the exact steps and messages to prepare.
3. **The draft-save branch of Create a Science Plan** is not implemented, as declared in the design file
   (team decision D6). The alternate flows that are implemented are the ones in section 3.
4. **Login** is out of scope, as the course requires; the actor identity arrives on the command line.
5. **AI review run 1 was against the working tree.** The review of section 6.3 read the files before the
   first commit; a second run against the final commit is planned, and its output will be added to
   `reviews/` and to section 6.3 with the real hash.
6. **The AI review's accepted findings are deliberate.** Findings 7, 13–31 and 34 are quality judgements
   on a deliberately small design; section 6.3 answers each one. The two Must-fix findings and nine
   Should-fix findings were real defects and were corrected in the design and the code.
