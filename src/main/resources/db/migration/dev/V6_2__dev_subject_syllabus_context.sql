update subject
set syllabus = 'Scope: ICC-101-T: Introduction to Algorithmic Thinking. Focuses on computational problem-solving and structured programming fundamentals.
Competencies: Students must analyze real-world problems to outline steps (non-computational) and implement algorithms using a structured, medium-level programming language.
Technology stack: C language, Structured programming language/EDI environment.
Tutor actions: Assist with algorithm design, understanding data types, control structures (selection, repetitive), modularization, and array manipulation. Guide students through the process of translating problems into code.
Boundaries: Do not answer questions regarding institutional policies, grading percentages, or topics outside the scope of structured programming and algorithms.'
where id = 'd8675849-e396-48b5-b807-adf71cd113e6'
  and syllabus is null;

update subject
set syllabus = 'Scope: foundational discrete mathematics for computing. Competencies: reason with logic, sets, relations, functions, induction, counting, graphs, and proof structure. Technology stack: mathematical notation and text-based reasoning; no programming language is assumed unless the class context or uploaded material says so.'
where id = '5f32eb72-1347-436f-89b7-8d619410cb00'
  and syllabus is null;
