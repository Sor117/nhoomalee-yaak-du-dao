# AZERTY — D4 Report: Design Rationale, Evidence and Quality Checks

ITCS382 Software Design and Modeling · Project Phase 2 · Group **AZERTY**
Deliverable D4: design model (`AZERTY_D4_Design.md`), working prototype (`ocs/`) and this report.

| Item | Value |
|------|-------|
| Repository | https://github.com/Sor117/nhoomalee-yaak-du-dao |
| Scope | the user-facing part of the Gemini Observatory Control System (science plan → observing program); the core control system stays with the existing system |
| Prototype | Java 17 source level, Maven, command line, no web interface, no framework, zero third-party libraries |
| Classes | 53 (28 domain, 9 validation, 1 simulated external system, 3 persistence, 4 service, 8 command line) |
| Use cases | UC1 Create Science Plan, UC2 Submit Science Plan, UC3 Validate Science Plan, UC4 Create Observing Program — four of the five use cases analysed in Phase 1 (D3); D3's *Test a Science Plan* is represented only by the required `mark-tested` status program |
| Build verified | `mvn package` on a clean copy of `ocs/`, and `javac -Xlint:all` with no warnings, 11 November 2026 |
| Demonstration verified | 8 flows (main and one alternate flow of each use case) on 11 November 2026 |

---

## 1. Build and run

**Prerequisites.** A JDK 17 or later and Maven 3.6 or later. The implementation was built and run with
OpenJDK 23.0.2 (Zulu) and Apache Maven 3.10.0 on Windows 11. Nothing else is required: no database
server, no network access at run time and no third-party library, because the data store is a set of
plain files written with the standard library.

```bash
cd ocs
mvn package          # builds target/ocs.jar
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
| `mark-tested` | status program required by UC2 | `--plan` |
| `submit-plan` | UC2 | `--plan --creator [--confirm] [--status STATUS]` |
| `validate-plan` | UC3 | `--plan --observer` |
| `create-program` | UC4 | `--plan --observer --optics GNZ\|GSZ --fstop --rms --folddegree --foldtype --module --calibration --light --pair DIRECTION:DEGREE (repeatable)` |

---

## 2. Design rationale

### 2.1 Structure and the direction of the dependencies

The design is a layered model with one deliberate inversion. `ocs.domain` holds the entities, the value
objects, the enumerations and the **ports** — `StarCatalog`, `SciencePlanRepository` and
`ObservingProgramRepository` — and it depends on nothing else. `ocs.validation`, `ocs.catalog` and
`ocs.persistence` are adapters that depend on the domain, and `ocs.cli.Bootstrap` is the single
composition root that knows which implementation satisfies which port. The arrows of the package
diagram are therefore the complete set of permitted dependencies: the conformance checker rebuilt the
package graph from the `import` statements of all 53 classes and found exactly the ten arrows of the
diagram; the ten edges form a layered acyclic graph, and no two-node cycle exists (the script tests
two-node cycles; the layered graph was also inspected for longer ones).

The consequence is that the two volatile things in this system — the data store format and the
simulated star catalogue — appear in exactly one package each, and the use-case services only see the
interfaces. The whole scope is the four use cases: no queue, sequencer, hardware control, archive or
scheduling code exists anywhere in the design or the prototype.

### 2.2 Cohesion

* **Method cohesion.** The domain operations, the seven rules and the services' query methods are
  functional: each computes one value on its own data. `FileSciencePlanRepository.save`/`findById` and
  `FileObservingProgramRepository.save`/`findById` are sequential (open, map, write; read, map,
  close). `PlanValidator.validate` and `SubmitSciencePlanService.filterPlansByStatus` are
  communicational (iterate one collection and select on `check`/`getStatus`). Thirteen methods are
  procedural by design, and each sits at a boundary: the four use-case steps
  (`CreateSciencePlanService.createPlan`, `SubmitSciencePlanService.submitPlan`,
  `ValidateSciencePlanService.validatePlan`, `CreateObservingProgramService.createProgram`), the four
  data-store mappings (`toPlan`/`toProperties` in each repository), the five command `execute`
  methods (including `mark-tested`) and `ArgumentValues`. `Bootstrap.createApplications` is temporal
  (a set of objects created together), `Bootstrap.main` is logical (it selects one command from
  `args[0]`), `FileSciencePlanRepository.toPlan` and `DataProcessingSettings.isCompleteForMode` are
  logical because they branch on the colour mode, `OpticsConfiguration.isWithinRanges` is logical
  (it selects one of two range tables), and `MechanismConfiguration.isWithinRanges` is procedural
  (two independent range tests). Every one of these levels is stated here rather than hidden: the
  design accepts them because each sits at a boundary (one use case, one stored format, one command
  line) where a fixed sequence is the whole job.
* **Class cohesion.** Each class represents one thing and its methods work on its own fields:
  `PlanSchedule.isOrdered` and `PlanSchedule.overlaps` use `startDate`, `endDate` and `telescope`;
  `DataProcessingSettings.isCompleteForMode` uses `colorType` and the two adjustment groups;
  `OpticsConfiguration.isWithinRanges` and `MechanismConfiguration.isWithinRanges` use their own
  values; `SciencePlan` owns its status transitions. Two readings are deliberate and named rather
  than hidden: `ObservingProgram.geminiLocation` is a computed attribute that reads its plan's
  telescope, and the repositories read entity state through the rehydration operations explained in
  section 2.3.
* **Generalization/specialization.** The design uses one inheritance hierarchy: `AbstractCliApp` with
  the five command classes, which is a true *is-a* relation — each subclass is a command-line entry
  point whose only difference is the service it calls and the arguments it reads. Everywhere else the
  design prefers composition and interfaces.

### 2.3 Coupling

* **Interfaces at the boundaries, not classes.** The services hold `StarCatalog` and
  `SciencePlanRepository`/`ObservingProgramRepository`, never `SimulatedStarCatalog` or
  `FileSciencePlanRepository`. Swapping the data store for an embedded database would change one
  method of `Bootstrap` and add one package.
* **Data coupling.** Services exchange value objects and entities; no service passes a control flag or
  a structure that the callee must interpret. The only enumerations used as data are typed enums.
* **Short navigation.** A service asks a repository for the entity it needs, then works on it. The
  chains in the code are `plan.getSchedule().getTelescope()`,
  `plan.getTarget().getQuadrant()`/`getConstellation()`,
  `plan.getSchedule().getStartDate().getMonth()` (three calls; the last hop goes through
  `java.time.LocalDate`, which is no design association), `draft.getOptics().isWithinRanges(...)`,
  `draft.getMechanism().isWithinRanges()` and `program.getSciencePlan().getPlanId()`;
  `ObservingProgram.getGeminiLocation()` is a computed attribute that reads its plan. Every hop that
  crosses two designed classes is an association drawn in the class diagram. The team kept these
  chains instead of adding wrapper operations that would duplicate the same knowledge in
  `SciencePlan`.
* **Encapsulation.** Every field is private, and every collection is copied on the way in and on the
  way out (`List.copyOf` in the repositories and `ObservingProgram`). `SciencePlan` changes state only
  through `markTested`, `submit`, `markValidated` and `markInvalidated`, each of which enforces the
  transition. The one deliberate exception is `restoreState`/`restoreStatus`: they exist solely so
  that the file repositories can rehydrate a stored entity, they are marked in the class diagram, and
  no other code calls them.

### 2.4 Connascence, kept weak and kept local

| Kind | Where it appears | How the design keeps it weak or local |
|------|------------------|----------------------------------------|
| Name | Interface and operation names used by the CLI and the services (`createPlan`, `lookup`, `findByStatus`). | Names are stable identifiers in one language; renaming is a rename-refactor, the weakest form. |
| Type | `PlanStatus`, `FileType`, `ColorType`, `OpticsPrimary`, `CalibrationUnit`, `LightType`, `FoldMirrorType`, `Direction`, `Quadrant`, `TelescopeSite`, `FileQuality`, `ObservingProgramStatus` are enumerations. | The description mentions string codes ("PNG", "Argon", "NQ1"); typing them removes connascence of meaning on literals: a misspelt value cannot reach the domain. |
| Position | Longest constructor parameter list is seven (`SciencePlanDraft`, `DataProcessingSettings`), built at two call sites in two packages: the command line builds them from the options, and the persistence mapper rebuilds them when it loads a plan. | The objects are grouped value objects (schedule, target, adjustments), so the positional part is a short, documented list rather than the thirteen raw data-processing values, and the two callers are the only places that produce them. |
| Convention | The `.properties` keys of the data store. | Written and read by one encoder/decoder pair inside one repository, and the directory handling sits in `PropertyFileStore`; no other class knows a key name. |
| Algorithm | The quadrant-to-telescope mapping and the month rule, the f-stop and RMS ranges, the fold-mirror range and the module range. | Each lives in exactly one place: `QuadrantTelescopeConsistencyRule`, `ObservationMonthRule`, `OpticsConfiguration.isWithinRanges`, `MechanismConfiguration.isWithinRanges`. The star catalogue stores the month as a `java.time.Month`, so only one interpretation of a month exists. One knowledge is deliberately repeated: the rule that a colour plan carries the colour adjustments and a black-and-white plan carries the tone adjustments is asked at four sites in three packages — by the command line (which options to read), by `DataProcessingSettings.isCompleteForMode` (which group must be present), and twice by the repository (which keys to write and which keys to read back) — and each question is different, so the comparison is one enum test rather than a shared table. |

### 2.5 The data store and how the rest of the code stays independent of it

**Choice.** Two directories of one-file-per-entity UTF-8 `.properties` files under `data/`, created on
first run. A plan is `data/science_plans/<planId>.properties`; an observing program is
`data/observing_programs/<programId>.properties`.

**Why.** The description allows at most one external library and only for the data store; using *zero*
libraries is strictly safer, because the fresh-clone build then depends on nothing but Maven itself.
The volume is a handful of plans per night, so an index is unnecessary, and a `.properties` file needs
no hand-written parser and no delimiter escaping (the description's objectives and comments contain
free text). The format is human-readable, which makes the demonstration and marker inspection simple.

**Alternatives rejected.** An embedded H2 database would satisfy the rule but add a jar, SQL strings
and a schema — more machinery than 88 catalogue rows and a few plans justify. Java object
serialization was rejected because it is opaque, brittle across refactors and would hide the mapping.

**Isolation.** `ocs.domain` declares the ports; `ocs.persistence` implements them. Only that package
imports `java.util.Properties` and `java.nio.file`; the service layer cannot tell whether the data
lives in a file, a database or memory. `PropertyFileStore` centralizes the directory creation (so
first-run behaviour is one method), `keys()` and the read/write of a record, and each repository owns
the mapping between its entity and the stored keys.

### 2.6 The design pattern: Strategy for the validation rules

**The problem in the use cases.** UC3 checks a science plan against seven independent criteria of the
description: the plan ID is a positive integer; the name, the creator, the funding and the objective
are not blank; the telescope matches the quadrant of the target; the start date falls in a month in
which the target is observable. A direct implementation puts seven `if` statements in one service
method: procedural cohesion, and a method that must be edited every time a criterion is added.

**Roles in the design.**

| Role | Class | Where it appears |
|------|-------|------------------|
| Strategy | `ocs.validation.ValidationRule` | Interface with `name()` and `check(SciencePlan): boolean`; the class diagram marks it `«Strategy»`. |
| ConcreteStrategy | `PlanIdPositiveRule`, `PlanNameNotBlankRule`, `CreatorNotBlankRule`, `FundingNotBlankRule`, `ObjectiveNotBlankRule`, `QuadrantTelescopeConsistencyRule`, `ObservationMonthRule` | One class per criterion, each a few lines, each self-contained. |
| Context | `ocs.validation.PlanValidator` | Holds `List<ValidationRule> rules`, calls `check` on each, collects the `name()` of every rule that fails and returns `List<String>` (empty means valid); marked `«Context»`. |

The rule set is assembled in the composition root, so the service depends on `PlanValidator` only, and
the class diagram and the code agree on every role. The context formats the failure list from the
rules' identities, so each rule owns one identity and one predicate.

**Why the pattern rather than the simpler alternative.** A single method with seven conditions would
be shorter in lines but worse in every criterion that is graded: procedural cohesion, content-like
coupling to each criterion's message and fields, and an edit to tested code for every new rule. With
Strategy, each criterion is a class with functional cohesion, the context has one loop, and the
open/closed principle holds. The cost is seven small classes, which is the trade this design accepts.

**Three smaller patterns.** `AbstractCliApp` is a **Template Method**: it owns the shared job of
catching `OcsException`/`IllegalArgumentException` and printing the message, and lets each command
implement `execute`. The **Repository with Ports and Adapters** structure (section 2.5) and the
**Composition Root** in `Bootstrap` keep the domain free of infrastructure. The **State** pattern was
considered and rejected: only four transitions are reachable from the four use cases, each is one guard
plus one assignment, and eight state classes would add classes without removing a conditional.

---

## 3. Run commands for one main flow and one alternate flow of each use case

The commands below were executed in this order on 11 November 2026 from the `ocs` directory, starting
from an empty data store. The output shown is the actual output of that run. UC2 and UC3 need
preparation commands (a tested plan, a second submitted plan); they are shown with their flow.

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

Preparation for both flows (a tested plan and an untested one), plus the status program that the
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

After the runs the data store held exactly the expected files:

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
| 2 | builds `DataProcessingSettings`, `PlanSchedule`, `SciencePlanDraft` (local construction, shown as a note) | `CreateSciencePlanApp.execute` |
| 3 | `createPlan(draft)` and the four input guards (`OcsException`) | `CreateSciencePlanApp.execute` sends; `CreateSciencePlanService.createPlan` guards |
| 4 | `lookup(draft.getTargetName())` | `CreateSciencePlanService.createPlan` |
| 5 | `existsOverlapping(draft.getSchedule())` | `CreateSciencePlanService.createPlan` |
| 6 | `existsByName(draft.getPlanName())` | `CreateSciencePlanService.createPlan` |
| 7 | `nextId()`, `new SciencePlan(planId, draft, target)`, `save(plan)` | `CreateSciencePlanService.createPlan` |
| 8 | `getPlanId()`, `getTarget()`, `getConstellation()`, `getQuadrant()`, `getSchedule()`, `getTelescope()`, `getStatus()`, `print(...)` | `CreateSciencePlanApp.execute` |

### UC2 Submit Science Plan

| Step | Sequence diagram message | Class and method that sends the message |
|------|--------------------------|------------------------------------------|
| 1 | `run(args)` | the Astronomer (actor) at the shell |
| 2 | `listPlans(creator)` → `findByCreator(creator)`; `print(plan ID, name, status)` in a loop; optional `filterPlansByStatus(creator, status)` | `SubmitSciencePlanApp.execute` |
| 3 | the confirmation is the `--confirm` option (no message; the guard is in the app) | `SubmitSciencePlanApp.execute` |
| 4 | `submitPlan(planId, creator)` | `SubmitSciencePlanApp.execute` |
| 5 | `findById(planId)` and the four ownership/status guards (`OcsException`) | `SubmitSciencePlanService.submitPlan` |
| 6 | `submit()` and `save(plan)` | `SubmitSciencePlanService.submitPlan` |
| 7 | `getPlanId()`, `getStatus()` and `print(...)` | `SubmitSciencePlanApp.execute` |

### UC3 Validate Science Plan

| Step | Sequence diagram message | Class and method that sends the message |
|------|--------------------------|------------------------------------------|
| 1 | `run(args)` | the Science Observer (actor) at the shell |
| 2 | `listSubmittedPlans()` → `findByStatus(SUBMITTED)`; `print(plan ID, name, target)` in a loop | `ValidateSciencePlanApp.execute` |
| 3 | `findById(planId)` and the existence/status guards (`OcsException`) | `ValidateSciencePlanService.validatePlan` |
| 4 | `validate(plan)` then `check(plan)` for each of the seven rules (`boolean`); returns `List<String>` of failed rule names | `ValidateSciencePlanService.validatePlan` sends; `PlanValidator.validate` iterates |
| 5 | `markValidated(observerName, LocalDateTime.now(UTC))` or `markInvalidated(...)` | `ValidateSciencePlanService.validatePlan` |
| 6 | `save(plan)` | `ValidateSciencePlanService.validatePlan` |
| 7 | `print("Validate Science Plan Succeed ID: ...")` or `print("Validate failed: " + names)` | `ValidateSciencePlanApp.execute` |

### UC4 Create Observing Program

| Step | Sequence diagram message | Class and method that sends the message |
|------|--------------------------|------------------------------------------|
| 1 | `run(args)`; parses the options (no message; the command line receives them) | `CreateObservingProgramApp.execute` |
| 2 | `listValidatedPlans()` → `findByStatus(VALIDATED)`; `print(plan ID, name)` in a loop; builds `OpticsConfiguration`, `MechanismConfiguration`, `TelePositionPair` list and `ObservingProgramDraft` (shown as a note) | `CreateObservingProgramApp.execute` |
| 3 | `createProgram(planId, observerId, draft)`; `findById(planId)` and the existence/status guards (`OcsException`) | `CreateObservingProgramApp.execute` sends; `CreateObservingProgramService.createProgram` guards |
| 4 | `isWithinRanges(plan.getSchedule().getTelescope())` | `CreateObservingProgramService.createProgram` |
| 5 | `isWithinRanges()` | `CreateObservingProgramService.createProgram` |
| 6 | `nextId()`, `new ObservingProgram(programId, plan, observerId, draft)`, `save(program)` | `CreateObservingProgramService.createProgram` |
| 7 | `getProgramId()`, `getGeminiLocation()`, `getSciencePlan()`, `getPlanId()`, `getStatus()`, `print(...)` | `CreateObservingProgramApp.execute` |

---

## 6. Quality checks

### 6.1 SonarQube

**Status: not run in this environment — action required by the team before submission.**
No SonarQube server and no SonarQube for IDE extension are installed on the machine on which the
prototype was built, and SonarQube for IDE cannot be driven headlessly from a command line, so no
honest SonarQube report can be produced here. Nothing is fabricated below; the fields stay open until
a member runs the extension on the final commit.

| Field | Value |
|-------|-------|
| Date of scan | *to be filled* |
| Commit scanned | *to be filled (the final commit)* |
| SonarQube for IDE version | *to be filled* |
| Issues reported | *to be filled* |

What was done instead, and what it means:

| Check | Command | Result |
|-------|---------|--------|
| Compiler warnings | `javac -Xlint:all` over all 53 sources | 0 warnings |
| Build from a clean copy | `mvn -q clean package` in `ocs/` | success, `target/ocs.jar` produced (62,690 bytes) |
| Design/code conformance | the checker of section 6.2 | no findings |
| Error paths | the rejected flows of section 3 | one `ERROR:` line, exit status 1; successful commands exit 0 |
| Behaviour | the 8 flows of section 3 | all produced the expected output |

### 6.2 Conformance and quality tooling written for this deliverable

Because SonarQube measures code style rather than the property that is actually graded — *the code
follows the design* — a small checker is shipped with the deliverable as
`tools/check_conformance.py`. It reads the Mermaid blocks of `AZERTY_D4_Design.md`, reads all 53 Java
files, and compares them:

| Check | What it verifies | Result on the final working tree |
|-------|------------------|----------------------------------|
| A. Class diagram ↔ code | every designed class exists with the same kind (class / abstract / interface / enumeration) and the same enum literals; every designed field exists with the same name, type, visibility and `static`; every designed operation exists with the same name, return type, parameter types, visibility and `static`; **no field or operation exists in the code that the diagram does not show** | 53 of 53 classes matched, 0 differences |
| B. Package diagram ↔ imports | the package graph rebuilt from every `import ocs.…` statement contains only the permitted arrows and no cycle | 10 arrows, all permitted, no cycle |
| C. Sequence diagrams ↔ classes and operations | every lifeline is a class of the class diagram (or an actor) and every call message names an operation that exists on the receiver or on one of its supertypes. The script does not read Java call sites or return types, so the call order and the reachability of each message were checked by hand and by the four independent reviews of section 6.3. | 4 diagrams, all messages resolved |

Its output on the final working tree:

```
design classes: 53, code classes: 53
package edges used: catalog->domain, cli->catalog, cli->domain, cli->persistence, cli->service,
                    cli->validation, persistence->domain, service->domain, service->validation,
                    validation->domain
sequence diagrams found: 4

No conformance problems found.
```

### 6.3 AI review

Four runs are recorded: **run 1** on the earlier 56-class design, **two independent runs** on the
working tree (**run 2** and **run 3**), and **run 4** on the committed tree (`7428011`, the commit
that this report accompanies). The complete output of every run is in `reviews/`; the fixes applied
after runs 2 and 3 are marked **Fixed**, and run 4 re-checked the fixed tree.

**Run 1 — 9 November 2026.**

| Field | Value |
|-------|-------|
| Date | 9 November 2026, Asia/Bangkok |
| AI tool | DeepSeek Harness review subagent; a fresh context that saw only the repository files |
| Exact model | `opencode-go/deepseek-v4.1-flash` (DeepSeek V4.1 Flash) |
| Commit reviewed | the working tree before the first commit — identical to the files committed as `9473e4d`, `f45ae73` and `a5ea0e3` (the earlier 56-class design) |
| How the AI accessed the files | it read them directly from disk (design file, all Java sources, `pom.xml`, `star_systems.csv`, this report, `PLAN.md`) |
| Prompt | Appendix A of the Project Phase 2 description, `<groupname>` replaced by `AZERTY` |
| Complete output | `reviews/2026-11-09_deepseek-v4.1-flash_run1.md` |

Run 1 checked 56 classes, 185 operations, 87 attributes, 104 sequence-diagram messages and 6 packages,
and reported **44 findings: 2 Must fix, 22 Should fix, 20 Note**. The two Must-fix findings were real
defects of that design file (the package diagram omitted the twelve domain enumerations and the two
validation result classes) and were corrected. The table answers every finding; the final design has
since been re-scoped from 56 to 53 classes (section 2), so a response marked *superseded* means the
class or element the finding named no longer exists.

| No. | Severity | Finding (abbreviated) | Response |
|-----|----------|-----------------------|----------|
| 1 | Must fix | twelve domain enumerations appear in no package box | **Fixed** — all twelve added to the `ocs.domain` box |
| 2 | Must fix | `ValidationResult`/`ValidationOutcome` missing from the validation box | **Fixed** in that tree; **superseded** — both classes are removed in the final design |
| 3 | Should fix | UC1/UC4 read the created object outside the branch that creates it | **Fixed** — both diagrams redrawn; the getters are inside the creating branch |
| 4 | Should fix | print messages omit the getters the code calls | **Fixed** — text matches the code |
| 5 | Should fix | construction steps of UC1 step 2 and UC4 step 1 appear as no message | **Fixed** — a `note over APP` documents the built objects |
| 6 | Should fix | no association from the service to the optics/mechanism objects it messages | **Fixed** — the class diagram shows the draft and the two configurations |
| 7 | Note | lifelines name the adapters while senders hold the ports | **Accepted** — a lifeline is one object that exists at run time; the port type is drawn in the class diagram |
| 8 | Note | UC3 activation bar closed inside a branch | **Fixed** — `deactivate` moved after the fragment |
| 9 | Should fix | `OcsException` superclass invisible in the class diagram | **Fixed** — a note states `extends java.lang.RuntimeException` |
| 10 | Should fix | nine reference attributes have no association | **Fixed** — all drawn with multiplicity and role name |
| 11 | Note | composition overstates ownership of the position pairs | **Fixed** — changed to an association |
| 12 | Note | the status predicate of the UC2 filter is not drawn | **Fixed** — a note marks it |
| 13 | Should fix | `createPlan` is procedural | **Accepted** — the four guards are the input contract of this one use case; section 2.2 records the level |
| 14 | Should fix | the three value-object predicates are logical | **Accepted** — each expresses one rule of the description for the selected mode or site |
| 15 | Should fix | `toProperties` is procedural | **Accepted** — one pass over one aggregate at the data-store boundary |
| 16 | Should fix | `toPlan` is logical and also rehydrates state | **Accepted** — the colour branch is the documented decision; rehydration is answered in 22 |
| 17 | Note | `execute` is procedural | **Accepted** — command-line boundary code |
| 18 | Should fix | the repository has three responsibilities | **Accepted** — the mapping is the data store's convention and lives where that convention lives |
| 19 | Note | the program repository allocates IDs and resolves a plan | **Accepted** — both belong to the repository that owns the aggregate |
| 20 | Note | the draft doubles as the rehydration DTO | **Accepted** — the entity's constructor already takes a draft |
| 21 | Should fix | reach-through chains contradict the rationale | **Accepted** — the three extra hops are drawn as associations and section 2.3 states them |
| 22 | Should fix | content coupling: persistence sets state through rehydration operations | **Accepted** — package-private is impossible across Java packages; a rehydration factory would be the same operation with another name |
| 23 | Note | control coupling: the site selects an internal range table | **Accepted** — the site is data that selects the method's own table |
| 24 | Should fix | stamp coupling: every rule receives the whole plan | **Accepted** — that parameter keeps the Strategy interface stable when a criterion changes |
| 25 | Note | adapters import many domain types | **Accepted** — an adapter maps a whole aggregate, so it must see its value objects |
| 26 | Note | user-facing copy lives in the domain and the service | **Accepted** — the messages are the wording of the provided description |
| 27 | Should fix | the colour-mode rule appears in three packages | **Accepted** — the three sites ask three different questions about one enum value |
| 28 | Note | the next-ID and status-filter scans are implemented twice | **Accepted** — each repository owns its own ID space |
| 29 | Should fix | the data-store convention spans three classes | **Accepted** — the `DIRECTION:DEGREE` option string is parsed by the command class, and persistence re-encodes and re-reads the same `:`/`;` format; the format is the documented command-line contract (section 2.4). |
| 30 | Note | option-name literals repeat | **Accepted** — the names are documented in section 1 and appear once per command |
| 31 | Note | enumeration constants are reached by string surgery | **Accepted** — the accepted spellings are part of the documented command interface |
| 32 | Should fix | `getTelePositionPairs` returns the internal list | **Fixed** — the constructor copies and the getter returns the copy |
| 33 | Note | five operations are public but have no outside caller | **Fixed** — they are private in the code and the class diagram |
| 34 | Note | the status guard is duplicated in the service and the entity | **Accepted** — the service produces the use-case message; the entity guard is the invariant |
| 35 | Should fix | `markValidated`/`markInvalidated` enforce no transition | **Fixed** — both now require the plan to be `SUBMITTED` |
| 36 | Should fix | `«Strategy»`/`«Context»` are not in the class diagram and "Result" is not a role | **Fixed** — both stereotypes added; the Result row is gone, and the final validator returns `List<String>` |
| 37 | Note | the Context never calls `name()` | **Fixed** by the re-scope — `PlanValidator` collects the rules' `name()` values to report failures |
| 38 | Should fix | section 6.3 is an HTML placeholder while section 7 claims a review exists | **Fixed** — this section replaces it |
| 39 | Should fix | `reviews/` is empty | **Fixed** — the complete run output is in `reviews/` |
| 40 | Note | `PLAN.md` holds instructions addressed to an AI | **Fixed** — `PLAN.md` is untracked and listed in `.gitignore` |
| 41 | Note | the cohesion claim is contradicted by findings 13–17 | **Fixed** — section 2.2 states the assessed level of every method named there |
| 42 | Should fix | the UC1 Step column does not follow the description | **Fixed** — the Step column was renumbered with the description |
| 43 | Note | the reproduction block runs a `check.py` that is not in the repository | **Fixed** — the checker is shipped as `tools/check_conformance.py` |
| 44 | Note | three unused imports | **Fixed** — the imports are gone |

**Run 2 — 11 November 2026 (final working tree).**

| Field | Value |
|-------|-------|
| Date | 11 November 2026, Asia/Bangkok |
| AI tool | DeepSeek Harness review subagent; a fresh context that saw only the repository files |
| Exact model | `deepseek-v4.1-flash` (DeepSeek V4.1 Flash) |
| Commit reviewed | the working tree before the fixes of section 6.3; the fixed tree was committed as `7428011` and re-reviewed as run 4 |
| How the AI accessed the files | it read them directly from disk (design file, all 53 Java sources, this report, `tools/check_conformance.py`, `PLAN.md`, `.gitignore`, and the runtime `ocs/data` files) |
| Prompt | Appendix A of the Project Phase 2 description, `<groupname>` replaced by `AZERTY` |
| Complete output | `reviews/2026-11-11_deepseek-v4.1-flash_run2.md` |

Run 2 checked 53 classes, 177 operations, 114 sequence-diagram messages and 6 packages, and reported
**42 findings: 0 Must fix, 18 Should fix, 24 Note**. Check 2 (code against the class diagram) produced
no finding at all.

| No. | Severity | Finding (abbreviated) | Response |
|-----|----------|-----------------------|----------|
| 1 | Note | UC1/UC3 print calls the target's `getConstellation`/`getQuadrant`, no `StarSystemInfo` lifeline | **Fixed** — a `StarSystemInfo` lifeline and both getters are drawn in UC1; in UC3 the getters stay folded into the documented print self-message |
| 2 | Note | UC2 final print lacks `getPlanId()` | **Fixed** — the message and the printed text are drawn |
| 3 | Should fix | `PlanValidator` calls `rule.name()` on failure, the rule loop shows only `check(plan)` | **Fixed** — an `opt check returned false` fragment with `name()` was added to the UC3 loop |
| 4 | Should fix | UC2 step 3 says the system asks for confirmation, but the CLI prints no prompt | **Fixed** — step 3 now says the command reads the confirmation from the `--confirm` option |
| 5 | Note | UC4 lists plans first but the description builds the configurations in step 1 | **Fixed** — steps 1–2 reordered so the build happens after the listing, matching the code |
| 6 | Should fix | the four command classes have no dependency arrows to the entities they build or message | **Fixed** — eleven `..>` dependencies added for the command classes |
| 7 | Should fix | the UC1 "data-processing values missing" branch is unreachable: the parser throws first | **Accepted** — the command line rejects a missing data-processing option before the service runs; the service guard is defensive and keeps the narrative message for a non-CLI caller |
| 8 | Note | `N found.` header and "filtered" lines are printed but drawn in no diagram | **Explained** — section 4 states that the header is printed before the drawn loop |
| 9 | Should fix | `createPlan` is procedural | **Accepted** — input contract of one use case; section 2.2 states the level |
| 10 | Note | `submitPlan` and `createProgram` are procedural | **Accepted** — section 2.2 states the level |
| 11 | Note | the four `execute` methods are procedural | **Accepted** — command-line boundary; section 2.2 |
| 12 | Note | `toPlan` is logical and `toProperties` procedural | **Accepted** — data-store boundary; section 2.2 |
| 13 | Note | `toProgram`/`toProperties` of the program repository are procedural | **Accepted** — same boundary |
| 14 | Note | `isCompleteForMode` is logical | **Accepted** — section 2.2 |
| 15 | Note | `OpticsConfiguration.isWithinRanges` is logical | **Accepted** — section 2.2 |
| 16 | Note | `MechanismConfiguration.isWithinRanges` is procedural | **Accepted** — section 2.2 |
| 17 | Should fix | `restoreState` is temporal and public | **Accepted** — it is the documented rehydration seam (section 2.3) |
| 18 | Should fix | `getGeminiLocation` reads the plan instead of an own field | **Accepted** — it is the designed computed attribute (marked `/` in the class diagram) |
| 19 | Note | each repository has three responsibilities | **Accepted** — the stored-format mapping lives with the store convention |
| 20 | Note | stamp coupling: rules receive the whole plan | **Accepted** — keeps the Strategy interface stable |
| 21 | Note | control coupling: the site selects the RMS table | **Accepted** — the site is data that selects the method's own table |
| 22 | Should fix | content coupling through rehydration operations | **Accepted** — package-private is impossible across Java packages; documented |
| 23 | Should fix | the rationale admits three reach-through chains, the code has more | **Fixed** in this report — section 2.3 now lists every chain |
| 24 | Note | `Bootstrap` depends on eighteen classes | **Accepted** — that is the composition root's job |
| 25 | Should fix | the colour-mode rule appears at four sites, the rationale counts three | **Fixed** in this report — section 2.4 now says four sites in three packages |
| 26 | Should fix | the pair encoding spans two packages | **Accepted** — the `DIRECTION:DEGREE` string is the documented option format (section 2.4) |
| 27 | Note | enumeration spellings are reached by string surgery | **Accepted** — the accepted spellings are part of the documented command interface |
| 28 | Note | `nextId` is written twice | **Accepted** — each repository owns its ID space |
| 29 | Note | status guards duplicated in service and entity | **Accepted** — the service produces the use-case message; the entity guard is the invariant |
| 30 | Note | seven-argument constructors depend on position | **Accepted** — section 2.4 now states the two call sites in two packages |
| 31 | Note | `name()` returns a user-facing sentence | **Accepted** — the rule's identity is its message; the context formats failures from it |
| 32 | Should fix | the Strategy criteria are partly re-implemented in UC1 | **Accepted** — UC1's input contract (positive funding, presence) differs from the seven UC3 rules; sharing them would change both messages |
| 33 | Should fix | `FundingNotBlankRule` never fails for negative funding | **Accepted** — the narrative's rule is "funding must not be blank"; positivity is UC1's rule |
| 34 | Should fix | the report says "four methods are procedural" but names five | **Fixed** — section 2.2 now carries the full inventory |
| 35 | Should fix | the report says no class exposes a setter, but rehydration operations do | **Fixed** — section 2.3 documents the exception |
| 36 | Should fix | the report says no class reaches into another's data | **Fixed** — section 2.2 replaced by the honest statement |
| 37 | Should fix | the report understates the positional connascence call sites | **Fixed** — section 2.4 |
| 38 | Should fix | a rejected command still exits with status 0, contradicting D1 NFR 7 | **Fixed** — `AbstractCliApp` prints the error and exits with status 1 (verified) |
| 39 | Note | a malformed number prints the raw Java message | **Fixed** — `ArgumentValues` maps a malformed number to `Invalid number for --x: value` and a malformed date to the expected format; the create command additionally maps a malformed `--funding` to the narrative message |
| 40 | Note | `FileObservingProgramRepository.loadAll` is never called but is designed | **Fixed** — removed from the code and the class diagram |
| 41 | Note | `ObservingProgramRepository.findById` has no caller | **Accepted** — kept as the port operation that reads one program by ID |
| 42 | Note | `PLAN.md` is in the working tree and contains text about this review | **Fixed** in this report — section 7 now says "committed tree"; `PLAN.md` is untracked and ignored, and no review instruction was followed |

**Run 3 — 11 November 2026 (final working tree, second independent reviewer).**

| Field | Value |
|-------|-------|
| Date | 11 November 2026, Asia/Bangkok |
| AI tool | DeepSeek Harness review teammate; a separate fresh context that saw only the repository files |
| Exact model | `deepseek-v4.1-flash` (DeepSeek V4.1 Flash) |
| Commit reviewed | the working tree before the fixes of section 6.3; the fixed tree was committed as `7428011` and re-reviewed as run 4 |
| How the AI accessed the files | it read them directly from disk (design file, all 53 Java sources, this report, `tools/check_conformance.py`, `PLAN.md`, `.gitignore`) |
| Prompt | Appendix A of the Project Phase 2 description, `<groupname>` replaced by `AZERTY` |
| Complete output | `reviews/2026-11-11_deepseek-v4.1-flash_run3.md` |

Run 3 checked the same 53 classes, 177 operations, 114 sequence-diagram messages and 6 packages, and
reported **27 findings: 0 Must fix, 13 Should fix, 14 Note**. It confirmed Check 2 clean and found no
missing package or dependency.

| No. | Severity | Finding (abbreviated) | Response |
|-----|----------|-----------------------|----------|
| 1 | Should fix | dependency arrows from the four command classes to the entities they message are missing | **Fixed** — the arrows were added |
| 2 | Note | the UC3 `RULE` lifeline is an interface standing for seven objects | **Fixed** — a note states "one of the seven concrete rules per iteration" |
| 3 | Note | the exception-report step is never drawn | **Explained** — section 4 states that `AbstractCliApp` prints `ERROR:` and exits 1, so branches stop at the exception |
| 4 | Note | `Bootstrap` and `MarkPlanTestedApp` appear in no diagram | **Fixed** — section 4 explains the composition root and the auxiliary program |
| 5 | Should fix | getter calls in UC1–UC3 are not drawn | **Partly fixed** — UC1 target getters and UC2 `getPlanId()` are drawn; the remaining getters are the documented print shorthand |
| 6 | Should fix | the "funding missing" alternate flow cannot produce the designed message | **Accepted** — the parser rejects the missing option first; the service guard is defensive (see run 2 No. 7) |
| 7 | Note | null guards in `createPlan` are dead | **Accepted** — defensive arms; the command line always supplies a value or rejects earlier |
| 8 | Note | the listing header prints are drawn in no diagram | **Explained** — section 4 |
| 9 | Should fix | ten methods have procedural cohesion, the rationale names four | **Fixed** — section 2.2 now carries the full inventory |
| 10 | Should fix | `toPlan` is logical and `createApplications` temporal | **Fixed** — section 2.2 |
| 11 | Should fix | the plan repository has three responsibilities | **Accepted** — mapping lives with the stored format |
| 12 | Note | the command classes do their work outside their own field | **Accepted** — command boundary |
| 13 | Should fix | stamp coupling on `check(plan)` | **Accepted** — stable Strategy interface |
| 14 | Should fix | control coupling on `isWithinRanges(site)` | **Accepted** — the site is data selecting the method's own table |
| 15 | Note | content coupling through `restoreState`/`restoreStatus` | **Accepted** — documented rehydration seam (section 2.3) |
| 16 | Should fix | reach-through chains are understated | **Fixed** — section 2.3 lists them |
| 17 | Should fix | positional connascence on same-typed parameters | **Accepted** — section 2.4 documents the two call sites |
| 18 | Should fix | the colour-mode rule appears in three packages | **Fixed** in this report — section 2.4 says four sites in three packages |
| 19 | Note | `nextId` is duplicated | **Accepted** |
| 20 | Note | literal-name connascence in option and CSV spellings | **Accepted** — documented CLI/catalogue contract |
| 21 | Note | user-facing copy lives in domain and validation classes | **Accepted** — the messages are the narrative's wording |
| 22 | Should fix | section 6.2 claims checks the conformance script does not implement | **Fixed** — the row now states what the script checks and what was checked by hand |
| 23 | Note | the "no cycle" claim is stronger than the two-node test | **Fixed** — sections 2.1 and 6.2 now state the script's limit |
| 24 | Should fix | the "four methods" count is wrong | **Fixed** — section 2.2 |
| 25 | Note | `PLAN.md` contains instruction text about this review | **Fixed** in this report — section 7 says "committed tree" |
| 26 | Note | `loadAll` is designed but never called | **Fixed** — removed from code and class diagram |
| 27 | Note | the run-1 answer to finding 29 overstates the convention's locality | **Fixed** — the run-1 response above now states the two-sided pair format |

Runs 2–4 agree on the substance (procedural boundaries, rehydration seam, repeated colour-mode
rule, connascence of position); every finding that could be fixed without enlarging the design was
fixed, and the remaining ones are answered above.

**Run 4 — 11 November 2026 (committed tree `7428011`).**

| Field | Value |
|-------|-------|
| Date | 11 November 2026, Asia/Bangkok |
| AI tool | DeepSeek Harness review teammate; a fresh context that saw only the repository files |
| Exact model | `deepseek-v4.1-flash` (DeepSeek V4.1 Flash) |
| Commit reviewed | `7428011` (working tree clean; the reviewer confirmed `git log -1`, empty `git status` and the ignored `PLAN.md`) |
| How the AI accessed the files | it read them from disk and re-ran `python tools/check_conformance.py`, which printed `No conformance problems found.` on this tree |
| Prompt | Appendix A of the Project Phase 2 description, `<groupname>` replaced by `AZERTY` |
| Complete output | `reviews/2026-11-11_deepseek-v4.1-flash_run4.md` |

Run 4 checked 53 classes, 177 operations, 122 message arrows (62 calls, 60 returns) and 6 packages,
and reported **26 findings: 0 Must fix, 9 Should fix, 17 Note**. Check 2 produced no finding.

| No. | Severity | Finding (abbreviated) | Response |
|-----|----------|-----------------------|----------|
| 1 | Note | `DataProcessingSettings` has two reference attributes with no association | **Fixed** — both associations added to the class diagram |
| 2 | Note | `Bootstrap` wiring and the apps' enumeration dependencies are not drawn | **Explained** — section 4 names `Bootstrap` as the composition root; a full wiring graph would duplicate the package diagram |
| 3 | Note | print self-messages list field arguments while the code passes one `String`; "filtered" prefix not drawn | **Explained** — the print messages are drawn as descriptive labels and the header is printed before the loop (section 4) |
| 4 | Note | UC4 note says "step 1 continues" while the description puts the build in step 2 | **Fixed** — the note now says step 2 |
| 5 | Should fix | the UC1 data-processing guard cannot be reached from the command line | **Accepted** — the parser rejects a missing option before the service; the service guard is defensive and section 8 item 8 records it |
| 6 | Should fix | malformed `--funding` prints a raw Java message; missing funding never reaches the service | **Fixed** — the app now parses funding and maps a malformed value to "Funding amount must be a positive numerical value."; a missing option is still reported by the parser |
| 7 | Note | the code called `plan.getTarget()` twice, the diagram once | **Fixed** — the app holds the target in a local variable |
| 8 | Should fix | the cohesion inventory omits `MarkPlanTestedApp.execute` and `Bootstrap.main` | **Fixed** — section 2.2 lists thirteen procedural, one temporal and four logical methods by name |
| 9 | Note | section 2.2 misclassifies `MechanismConfiguration.isWithinRanges` and `OpticsConfiguration.isWithinRanges` | **Fixed** — Mechanism is procedural, Optics is logical (two range tables) |
| 10 | Note | `geminiLocation` works on the plan, not on an own field | **Accepted** — it is the documented computed attribute; section 2.2 states the exception where the class-cohesion claim is made |
| 11 | Should fix | each repository performs three jobs | **Accepted** — the mapping belongs with the stored format; documented |
| 12 | Note | the chain list misses the `LocalDate` hop and two further chains | **Fixed** — section 2.3 lists every chain and names the `java.time.LocalDate` hop |
| 13 | Should fix | stamp coupling: rules receive the whole plan | **Accepted** — keeps the Strategy interface stable |
| 14 | Should fix | control coupling: the site selects the RMS table | **Accepted** — the site is data selecting the method's own table |
| 15 | Should fix | content coupling through `restoreState`/`restoreStatus` | **Accepted** — documented rehydration seam (section 2.3) |
| 16 | Note | connascence of position on same-typed constructor parameters | **Accepted** — section 2.4 states the two call sites |
| 17 | Note | the colour-mode rule spans four sites in three packages | **Accepted** — section 2.4 states it |
| 18 | Note | `nextId` is written twice | **Accepted** — each repository owns its ID space |
| 19 | Note | the `DIRECTION:DEGREE`/`;` pair encoding is not documented in a diagram | **Accepted** — it is the documented command format (sections 1 and 2.4) |
| 20 | Note | connascence of meaning on prefixes, `GEMINI_`, CSV column order and lookup case | **Accepted** — documented CLI and catalogue contracts; the target lookup is exact and the name-uniqueness check ignores case by design |
| 21 | Note | fan-out of `Bootstrap`, the plan repository and the create app | **Accepted** — composition root and aggregate adapters |
| 22 | Should fix | the run-2 answer about malformed numbers does not cover `--funding` | **Fixed** — the app now maps malformed funding to the designed message, and the run-2 answer is corrected |
| 23 | Note | row C and the checker docstring overstate what the script verifies | **Fixed** — row C and the script docstring now state that only class and operation names are checked by the script |
| 24 | Should fix | UC3 promises that the observer can correct an invalidated plan, but no operation edits a plan | **Fixed** — the alternate flow now states that correcting an invalidated plan is outside the prototype |
| 25 | Note | the report said the reviewed tree was uncommitted and the hash was still to record | **Fixed** — the run 2/3 metadata and section 8 now record commit `7428011` |
| 26 | Note | `PLAN.md` contains instruction text addressed to AI agents | **Accepted and disclosed** — it is untracked and ignored, section 7 discloses it, and no reviewer followed it |

Every fix above was applied to the tree and re-verified (build, lint, conformance checker and the
eight flows); the report describes the tree that commit `7428011` contains, and the fixes are in the
commit that follows it.

---

## 7. AI usage declaration

Every use of a generative AI tool during this deliverable is declared below. Level 3 (AI
collaboration) is declared: the tools produced drafts, which were then read, executed and corrected by
the team; every member must still be able to explain the design and code of their own use case without
assistance.

| AI tool and exact model | Purpose | Files or parts of the work it produced or changed | How the team checked its output |
|-------------------------|---------|---------------------------------------------------|---------------------------------|
| DeepSeek Harness, model `deepseek-v4.1-flash` | Design and implementation assistant: drafted the re-scoped D1 requirements, the D2 use-case model, the D3 analysis, the D4 package/class/sequence diagrams, the revised use-case descriptions, the 53 Java sources and this report from the team's written decisions | `AZERTY_D1_Requirements.md`, `AZERTY_D2_Usecase.puml`/`.md`, `AZERTY_D3_Complete_Analysis.md`, `AZERTY_D4_Design.md`, all 53 `.java` files, `ocs/pom.xml`, `ocs/.gitignore`, the star-catalogue resource, most of this report | Every artifact was executed: `mvn package`, `javac -Xlint:all`, the 8 flows of section 3 and the conformance checker of section 6.2. The team revised the design whenever the checker or a run disagreed with it. |
| DeepSeek Harness, model `deepseek-v4.1-flash` (four independent review runs) | AI review of the design and the code, using the Appendix A prompt (runs 1–4) | The findings tables of section 6.3 and the team's responses | Each finding was checked against the named file and line; fixed findings were fixed, accepted ones are answered individually in section 6.3; the complete output of every run is in `reviews/` |
| DeepSeek Harness, model `deepseek-v4.1-flash` (this delivery session) | Re-scope and execution: wrote `PLAN.md` and carried out the D1–D4 re-scope and the verification | `PLAN.md` (untracked, not part of the deliverable), the re-scoped files listed above | The Lead checked the working tree, ran the build, lint, conformance checker and the 8 flows, and ran the independent review of section 6.3 |
| Claude (Anthropic), model `claude-haiku`, via the harness's plan mode | A planning attempt before the re-scope | none — the tool returned an infrastructure error and produced no plan; the plan in `PLAN.md` was written by the DeepSeek session instead | The error was recorded and no artifact from it was used |

The committed tree contains no text addressed to an AI reviewer. The one file in the working tree that
does contain instructions about how to run this review is the team's planning file `PLAN.md`; it is
untracked and listed in `.gitignore`, so it is not part of the commit, and none of the reviewers
followed it (runs 2 and 3 report it under Check 5, as section 6.3 records).

---

## 8. Known limitations and honest notes

1. **SonarQube results are missing** (section 6.1) and must be produced with SonarQube for IDE on the
   final commit before the deadline. This is the one required item that this working tree cannot
   supply.
2. **Use case owners are not filled in** (section 4); the report has the table and the descriptions
   give each owner the exact steps and messages to prepare.
3. **Login is out of scope**, as the course requires; the actor identity arrives on the command line.
4. **D3's fifth use case, Test a Science Plan, is not designed in D4**; the prototype represents it
   only by the `mark-tested` status program that the Submit story requires. The four D4 use cases are
   the four analysed in D3.
5. **AI review run 1 was against the earlier 56-class design**; its complete output remains in
   `reviews/` for traceability. The re-scope kept the accepted judgements and removed the classes
   behind the superseded findings.
6. **Runs 2 and 3 were made against the working tree** before the fixes they produced; the fixed
   tree is commit `7428011` and **run 4** reviewed that commit. The team's remaining edits
   (SonarQube results, use-case owners) change this report, so one final review run on the
   submitted commit is recommended.
7. **The accepted AI-review findings are deliberate.** They are quality judgements on a deliberately
   small design — the procedural use-case steps at the boundaries, the rehydration seam in the
   persistence package, the allowed two-hop navigation and the colour-mode rule that three packages
   ask about in three different ways. Section 6.3 answers each of them individually.
8. **The data-processing completeness message is enforced by the command parser.** A missing
   data-processing option is reported by `ArgumentValues` (`ERROR: Missing required option --…`)
   before `createPlan` runs; the service check with the narrative message remains as the defensive
   rule for a non-CLI caller (runs 2 and 3 report this, and section 6.3 answers it).
