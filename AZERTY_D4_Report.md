# AZERTY — D4 Report: Design Rationale, Evidence and Quality Checks

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
| UC1 Create Science Plan | Peerawat |
| UC2 Submit Science Plan | Daranpob |
| UC3 Validate Science Plan | Warawuth, Pasit |
| UC4 Create Observing Program | Wachiravich, Patharapol |

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

**Status: scanned on 17 November 2026 across every file in the `ocs` folder.**
Every file in `ocs/` (54 files: 53 Java sources and `pom.xml`) was scanned using SonarQube / SonarLint (Sonar Java analyzer `8.44.0.48651`, SonarXML `2.19.0.8138`, SonarLint `6.1.1.79808`). The scan identified 7 issues (all code smells / maintainability recommendations; 0 bugs, 0 vulnerabilities, 0 security hotspots).

| Field | Value |
|-------|-------|
| Date of scan | 17 November 2026 |
| Commit scanned | `b29f93dbd17e4efab7e3cf201c773ad203eaa618` |
| SonarQube for IDE version | SonarLint 6.1.1.79808 / Sonar Java 8.44.0.48651 |
| Files scanned | 54 (53 Java source files + 1 `pom.xml`) |
| Issues reported | 7 |

#### SonarQube Issues

| No. | Rule | File:line | Message |
|:---:|:---|:---|:---|
| 1 | `java:S2677` | `ocs/src/main/java/ocs/catalog/SimulatedStarCatalog.java:43` | Use or store the value returned from "readLine" instead of throwing it away. |
| 2 | `java:S106` | `ocs/src/main/java/ocs/cli/AbstractCliApp.java:20` | Replace this use of System.out by a logger. |
| 3 | `java:S127` | `ocs/src/main/java/ocs/cli/ArgumentValues.java:25` | Refactor the code in order to not assign to this loop counter from within the loop body. |
| 4 | `java:S106` | `ocs/src/main/java/ocs/cli/Bootstrap.java:47` | Replace this use of System.out by a logger. |
| 5 | `java:S8696` | `ocs/src/main/java/ocs/domain/StarSystemInfo.java:29` | Use ".equals()" instead of "==" or "!=" to compare objects of value-based types. |
| 6 | `java:S1192` | `ocs/src/main/java/ocs/persistence/PropertyFileStore.java:32` | Define a constant instead of duplicating this literal ".properties" 5 times. |
| 7 | `java:S9391` | `ocs/src/main/java/ocs/validation/PlanValidator.java:20` | Use a stream instead of this loop. |

#### Notes on findings

* **`java:S2677` in `SimulatedStarCatalog.java:43`**: The first `reader.readLine()` deliberately skips the CSV header row (`star_system_name,available_month`). Assigning it to an unused local variable would trigger `java:S1481`.
* **`java:S106` in `AbstractCliApp.java:20` & `Bootstrap.java:47`**: The prototype is intentionally built with zero third-party logging dependencies as a pure command-line tool, so standard output via `System.out` is the designed user interface channel.
* **`java:S127` in `ArgumentValues.java:25`**: Incrementing `i` inside the loop body advances past the consumed option value (`--option <val>`); this is standard manual command-line argument parsing without external libraries.
* **`java:S8696` in `StarSystemInfo.java:29`**: `Month` is an enum (`java.time.Month`), where identity comparison `==` is safe and canonical in Java; the rule flags general value-based class comparisons.
* **`java:S1192` in `PropertyFileStore.java:32`**: The literal `".properties"` appears 5 times in path resolution within the file store; extracting a private `FILE_EXTENSION` constant is a benign cleanup.
* **`java:S9391` in `PlanValidator.java:20`**: The validation loop accumulates failed rule names into a list via an explicit `for` loop, preferred for straightforward imperative debugging over a stream pipeline.

Additional quality and build verifications:

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

...


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