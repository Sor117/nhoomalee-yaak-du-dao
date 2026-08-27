# Gemini Project Requirements Document

## 1.  Functional Requirements

**Science Program intake and validation**

1. The system shall receive and query Science Programs from the external science-program.  
2. The system shall interpret the science program hierarchy as Program , Observation, Sequence, and Step, respectively.  
3. The system shall expand structured iterators into an executable step list.  
4. The system shall validate a program at submission.  
5. The system shall immediately reject programs containing invalid command syntax or unresolvable physical conflicts.  
6. The system shall accept recoverable issues with warning.  
7. The system shall record the program version that is executed.

**Nightly queue support and recommendation**

8. The system shall send candidate observations to and receive execution results from the external scheduler.  
9. The system shall rank the next observation based on scientific priority, target airmass, remaining partner time, and observing conditions.  
10. The system shall show the reasons for each observation recommendation.  
11. The system shall never autonomously start a queued observation.  
12. For each recommendation, the system shall present the reason behind it.  
13. The system shall recompute and republish the ranking automatically whenever input changes.  
14. The system shall enable the operator to reorder, defer, preempt or force the execution of items in the night queue.  
15. The system shall inform the scheduler of each observation’s result and execution time so that the remaining allocated time can be updated correctly.

**Sequence execution and orchestration**

16. The system shall execute an observation step by step.  
17. The system shall issue independent preparation actions concurrently.  
18. The system shall enforce an explicit readiness barrier.  
19. The system shall support the operator command.   
20. The system shall accept a Target-of-Opportunity interrupt and safely suspend or abort the running sequence.  
21. The system shall track execution at step granularity so that a suspended observation resumes from the last completely acquired step.  
22. The system shall run calibration activities using the same sequencer and ensure that the collected data is linked to the science program that requested it.   
23. The system shall allow operations staff queue ad-hoc calibration outside the science plan.  
24. The system shall allow inactive instruments to perform calibration or remain in hot standby while another instrument uses the beam.  
25. The system shall block or send uncoordinated offset to any inactive-instrument action that would physically obstruct the beam.  
26. The system shall consider an observation complete only when the sequence has finished.  
27. The system shall capture the science observer’s quality verdict against the observation.  
28. The system shall act on quick-look results within a sequence while leaving major program changes to the operator.  
29. The system shall deliver commands to subsystems directly.  
30. The system shall interoperate with the generic subsystem command in the controller.  
31. The system shall require conformance only to newly integrated or visitor subsystems.

**Fault handling, safety and degraded operation**

32. The system shall isolate subsystem failures so that it does not affect other operational subsystems or abort active exposures on healthy instruments.   
33. The system shall ensure that an inactive instrument can’t block the beam and send unsynchronized movement commands to the telescope mount.

**Data assembly, hand-off and integrity**

34. The system shall send each dataset to the data-handling or archive system in FITS format with all header information.  
35. The system shall let additional users view an ongoing session without inferring with the observation.

**Logging, provenance and audit**

36. The system shall record all system events, commands, status changes, errors, and user actions, with a UTC timestamp and originator identity.  
37. The system shall maintain logs that allow the complete timeline of each night to be reconstructed, including commands, events, and user actions 

---

## 2.  Non-Functional Requirements

**Performance and Timing**

1.	The system must accept or reject commands within 2 seconds before any physical action occurs.   
2.	Command handshaking between controllers shall complete within 100-200 ms the acknowledgement protocol shall time out at approximately 500 ms and retry.  
3.	A request to a subsystem for status information shall be answered within 5 s, at any operational level.  
4.	A refreshed queue recommendation shall be published within 5 s of a change in observing-condition inputs.  
5.	The system shall sustain a peak control-information load of approximately 100 transactions per second.  
6.	The system shall measure and report shutter-open efficiency for every night.  
7.	The system shall not add measurable latency to readout, which ranges from \~0.1 s to 2–3 minutes.  
8.	The system’s warm start of the middle layer shall complete in approximately 1 minute.  
9.	The system shall operate a cold start in approximately 5 minutes.

**Capacity and scalability**

10.	The system shall support on-site retention of 7 days of raw data from instruments, with the most recent 3 days on fast storage for instant interactive retrieval.  
11.	The system shall support 6 active control nodes with 2 monitoring nodes and be designed to handle 10 active nodes.

**Reliability, availability and data integrity**

12.	The system’s failure must not take down another operation subsystem.  
13.	The system must not deadlock and be reproducible under concurrency stress testing.  
14.	The system shall contribute no more than 15 minutes of downtime per night to the observatory-wide availability budget, with all systemSystem-attributable downtime recorded and accounted for separately.  
15.	The system shall not restart itself when a physical hardware fails.  
16.	Baseline fault rates per subsystem shall be measured and used as the reference for drift detection.   
17.	Science data loss shall be zero with no exception.  
18.	Data redundancy shall prevent a single failure from destroying collected data.  
19.	The system shall support software reconfiguration around a failed unit instead of full hardware redundancy.

**Safety and Security**

20.	The system shall initiate the transition to a safe state within 2 s, and shall do so even if higher-level software has failed when detecting a danger condition.  
21.	The system shall be protected from Internet attacks, and essential control traffic shall use secure project-controlled connections.   
22.	Only the owning program team shall obtain proprietary data before it’s released in the access-control test matrix.

**Maintainability**

23.	The system shall be operable, and maintainable by a very small team.  
24.	The system shall be strictly modular.  
25.	The system shall be fully documented.  
26.	All system software shall be under version control, labelling in source and binary, with a retrievable version.  
27.	The system shall provide diagnostic and test modules for every subsystem interface at a usable maintenance level.

**Portability, deployment, and extensibility**

28.	The system shall be a single product deployed at both Gemini North and Gemini South sites with limited differences to local site configuration.  
29.	Maintaining one site should not require the other site to stop observing.

**Interoperability and standards**

30.	All delivered datasets shall be validated against the FITS standard with complete headers.  
31.	The motor and detector software shall not be rewritten.  
32.	The system shall let the remote user choose a preferable transmitted information where the remote link capacity is limited.  
33.	Offset between system timestamps and the observatory time reference shall be no more than 1 ms.
