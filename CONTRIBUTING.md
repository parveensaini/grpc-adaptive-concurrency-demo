# Contributing

Thanks for your interest in contributing to this project.

This repository is intentionally **small, focused, and reproducible**.  
Contributions should preserve that spirit and help practitioners better understand
overload behavior and adaptive concurrency.

---

## What Contributions Are Welcome

The most valuable contributions include:

- **Bug reports**
  - Clear reproduction steps
  - Logs and error messages
  - Expected vs actual behavior

- **Documentation improvements**
  - README clarity
  - Architecture explanations
  - Runbook or troubleshooting improvements

- **Observability improvements**
  - Clearer Grafana dashboards
  - Better metric naming or explanations
  - Safer, lower-cardinality metrics

- **Load patterns**
  - New traffic shapes (burst profiles, recovery patterns)
  - Edge cases that highlight overload behavior

- **Limiter experiments**
  - Tuning improvements
  - Alternative limiter configurations
  - Explanations of observed behavior

---

## What to Avoid

Please avoid:
- Adding heavy frameworks or unnecessary dependencies
- High-cardinality metrics (request IDs, user IDs, dynamic labels)
- Production-specific tuning that obscures the demo
- Features that hide or abstract away overload behavior

This repository is a **learning and exploration tool**, not a production framework.

---

## How to Contribute

1. Fork the repository
2. Create a branch:
   - `feat/<short-description>` for new features
   - `fix/<short-description>` for bug fixes
3. Keep changes **small and focused**
4. Verify everything works locally:

./gradlew clean build
docker-compose up
./gradlew :service:server:run
./gradlew :service:client:run


5. Open a Pull Request that clearly explains:
- What changed
- Why it matters
- How it was tested

---

## Code Style & Expectations

- Prefer **clarity over cleverness**
- Keep methods small and readable
- Use descriptive names
- Follow existing project structure
- Do not commit generated artifacts:
- `build/`
- `.gradle/`
- IDE files

---

## Questions & Discussion

- Use **GitHub Issues** for questions, ideas, or discussions
- Prefix issues with:
- `[question]` for questions
- `[idea]` for exploratory ideas
- `[bug]` for confirmed problems

---

## License

By contributing, you agree that your contributions will be licensed under the
**Apache License 2.0**, consistent with this project.

