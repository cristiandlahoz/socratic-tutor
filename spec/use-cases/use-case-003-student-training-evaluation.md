# Use Case 003: Student Training Evaluation

## Goal

Allow professors to launch a formative training activity for students, let students complete the assigned evaluation flow, and make the resulting report available to the professor.

## Actors

- Professor
- Student

## Main Flow

1. A professor creates a formative activity in the active class context.
2. The professor launches the draft activity.
3. The system creates one assignment per active student in the class.
4. The system emails students that the formative activity is available.
5. A student opens the assigned activity from the student workspace.
6. The student answers guided evaluation questions.
7. The system stores the transcript and final report on the assignment.
8. The professor opens the activity details and reviews student reports.

## Acceptance Criteria

- Draft activities can be launched only once.
- Launching an activity creates assignments only for unlocked student class members.
- Student workspace actions open the assigned evaluation route.
- Evaluation progress is persisted on `training_activity_assignment`.
- Professors can see assignment status and open completed reports.
