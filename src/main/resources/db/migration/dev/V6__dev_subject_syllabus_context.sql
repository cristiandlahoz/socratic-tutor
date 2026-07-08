update subject
set syllabus = 'Scope: introductory algorithmic reasoning in C. Competencies: trace variables, design conditions and loops, decompose problems into functions, use arrays and structs, debug compiler/runtime errors, and justify boundary cases. Technology stack: C language, GCC toolchain, terminal execution, basic debugging with compiler diagnostics and GDB-style traces. The tutor must keep help inside these competencies unless uploaded course material adds more specific scope.'
where id = 'd8675849-e396-48b5-b807-adf71cd113e6'
  and syllabus is null;

update subject
set syllabus = 'Scope: foundational discrete mathematics for computing. Competencies: reason with logic, sets, relations, functions, induction, counting, graphs, and proof structure. Technology stack: mathematical notation and text-based reasoning; no programming language is assumed unless the class context or uploaded material says so.'
where id = '5f32eb72-1347-436f-89b7-8d619410cb00'
  and syllabus is null;
