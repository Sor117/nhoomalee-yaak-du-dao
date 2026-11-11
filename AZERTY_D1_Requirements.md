# Gemini Observatory Control System — Requirements

## 1. Scope

The system under development is the **user-facing part of the Gemini Observatory Control System (OCS)**: creating, testing, submitting and validating science plans, creating observing programs, and viewing observation progress and data. The telescope, instruments, data archive and the existing low-level controllers remain the existing system and are represented by simulated/external interfaces.

## 2. Functional Requirements

The 21 functional requirements are numbered continuously and follow the observation workflow, from user access to the data that the astronomer finally views.

### 2.1 User access and administration

1. Users log in with an account; the role of the account determines which functions are available.

2. An administrator manages the user accounts and defines the access mode and the operation level of every user.

3. A telescope operator changes the observation schedule and defines the system shutdown time.

### 2.2 Creating and testing a science plan

4. An astronomer creates a science plan with an automatically generated plan ID, plan name, creator, funding in US dollars, objective, start and end dates, telescope site (Gemini North or Gemini South), target constellation taken from the star catalogue, and the data-processing requirements (file type, file quality, colour type, contrast, exposure, brightness, saturation, luminance, hue, highlights, shadows, whites, blacks).

5. The system rejects a science plan with the message defined for the rule that it breaks:
   - a missing plan name, objective or date: "Please complete all required fields before saving the science plan."
   - a funding value that is not a positive number: "Funding amount must be a positive numerical value."
   - a start date after the end date: "Start date cannot be after the end date."
   - data-processing values that are missing for the selected colour mode, or that are not valid decimal numbers: "Please complete all required fields for the selected data processing mode." and "Invalid input value. Please enter valid decimal numbers within the allowed range."
   - a target that is not in the star catalogue: "Selected target not found in the star catalogue. Please choose a valid target."
   - a date range that overlaps another plan on the same telescope: "Selected date range conflicts with another scheduled plan for this telescope."
   - a plan name that is already in use: "A science plan with this name already exists. Please use a different plan name."

6. An accepted plan is stored with status `CREATED`.

7. The astronomer tests the plan with the virtual telescope in interactive observing mode; the system provides the status-update program that takes a plan ID and moves the plan from `CREATED` to `TESTED`.

### 2.3 Submitting a science plan

8. An astronomer lists and filters their own science plans by status.

9. An astronomer submits a plan after confirming; only a plan in status `TESTED` can be submitted and it becomes `SUBMITTED`. An untested plan is rejected with "This science plan is incomplete. Please complete and test it before submission.", and a plan that is already submitted or executed is rejected with "This science plan has already been submitted or executed."

### 2.4 Validating a science plan

10. A science observer sees every plan in status `SUBMITTED`; when there is none, the system displays "There are currently no submitted science plans."

11. The system validates a submitted plan against the seven rules: the plan ID is a positive integer; the plan name, creator, funding and objective are not blank; the telescope matches the quadrant of the target (`NQ` → Gemini North, `SQ` → Gemini South); and the start date falls in a month in which the target is observable.

12. A plan that passes every rule becomes `VALIDATED` and a plan that fails any rule becomes `INVALIDATED`; the system records the observer's name and the date and time of the validation and reports the failed rules.

### 2.5 Creating an observing program

13. A science observer sees every plan in status `VALIDATED`; when there is none, the system displays "No validated science plans found."

14. The system creates an observing program from a validated plan with the optics primary, the f-stop, the optics secondary RMS, the science fold mirror degree and type, the module content, the calibration unit, the light type and the telescope position pairs; the program is linked to the science plan and records the observer who created it.

15. The system rejects observing-program values outside these ranges with an error message: f-stop 1.8–8.1 for GNZ and 2.9–18 for GSZ; optics secondary RMS 5–17 nm at Gemini North and 5–13 nm at Gemini South; science fold mirror degree in [30, 45]; module content 1–4.

### 2.6 Catalogue, operations, support and data

16. The system takes the quadrant and the observable month of every target constellation from the star catalogue and rejects an unknown target.

17. The science observer executes an approved science plan and monitors its progress; the astronomer can view the live telescope and the collected astronomical data. The telescope, instruments, archive and low-level controllers are outside this prototype.

18. The telescope operator validates an observing program before it is executed.

19. Support staff run system diagnosis, install new configurations and update the system to support newly added components.

20. The system stores science plans and observing programs in a local data store that it creates on first run, and it loads them again on the next run.

21. Every rejection is reported with the message defined for the rule that caused it, and nothing is stored for a rejected object.

## 3. Non-Functional Requirements

The prototype meets the following quality and implementation constraints; they are numbered separately from the functional requirements.

1. Java 17 or later, built with Maven; `mvn package` builds the prototype from a fresh clone.

2. Command-line interface: one command starts each use case; no web interface and no application framework.

3. At most one external library, only for the data store; the prototype uses none.

4. No separate database server; the data store creates its own files on first run.

5. A command answers within two seconds on the prototype's data volume.

6. A saved plan or program survives a restart; a rejected input leaves the store unchanged.

7. Errors are printed as clear messages with a non-zero exit status; no stack trace is shown to the user.

8. Only the owning astronomer may submit a plan, and only a science observer may validate one.

9. Source code is under version control and the design artifacts follow UML/Mermaid notation.
