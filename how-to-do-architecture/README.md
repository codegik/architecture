# How do we handle Architecture?

## Goals and non goals
Know what you're solving. If you can't explain it in one sentence, you're building a resume project.

Architecture without constraints is just drawing boxes. Know what you're NOT solving too, or you'll end up with a distributed system when all you needed was a cron job.

## Decision-Making & Process
Upfront planning vs emergent design? Here's the test: if you're wrong, how expensive is it? Building a payment system? Plan. Building an internal admin panel? Just start coding.

Trade-offs aren't made with frameworks, they're made at 2pm when your manager asks "can we ship Friday?" Everything is a beautiful, perfect system until someone mentions the deadline.

ADRs are great until you realize nobody reads them six months later. Your architecture is what's in the code, not in that Miro board from 2023.

"Good enough" is when it works and you can sleep at night. "Perfect" is what you tell yourself while your competitors ship features.

## Team Structure & Communication
Aligning stakeholders is simple: show them what failure costs. Sales wants features, ops wants stability, and your CEO read about microservices on LinkedIn. Pick your battles.

Onboarding new people? Make them fix a bug on day one. They'll learn your architecture faster than any wiki ever taught anyone anything.

Architectural disagreements? Data wins. "I think" loses to "I deployed it and here's what happened." If you can't settle it, build both and see which one survives production.

## Evolution & Change
Big rewrites are the architectural equivalent of "this time I'll really stick to my diet." Just start small. Strangler pattern exists because everyone learned the hard way.

Time to refactor? When you're afraid to change code. When new features take 3x longer than they should. When developers start asking if they can work on "literally anything else."

Technical debt is real debt: ignore it and it compounds. Pay a little every sprint or pay a lot when everything catches fire at 3am.

Legacy systems are like old relatives: you can't get rid of them, but you can stop inviting them to everything. Build new stuff in the new way, migrate when it makes sense, not when it feels right.

## Practical Challenges
Monolith vs microservices? If you have 5 developers, you have a monolith. Don't let anyone tell you different. Microservices solve organizational problems, not technical ones. You earn them with pain, not with enthusiasm.

POCs are supposed to be thrown away. If your POC is still running in production 18 months later, congratulations, that's just called development.

Consistency vs autonomy is a false choice. Have standards for things that cross boundaries (auth, logging, errors). Let teams do whatever they want inside those boundaries.

Security, performance, observability: these aren't features you add later. They're vegetables. Nobody gets excited about them, but you'll regret skipping them.

## Context-Specific
A 3-person startup and a 300-person company have nothing in common architecturally. Stop copying Netflix's architecture when you have 100 users.

Budget, timeline, compliance: these aren't constraints, they're reality. Architecture is the art of building the best system you can with what you actually have, not what you wish you had.


# Do you read the code?
Yes. Always. Architecture lives in code, not slides.

The diagram says "clean layers." The code says "everything imports everything." Guess which one is real?

You can't spot architectural drift from a Confluence page. You spot it when you see 47 different ways to make an HTTP call.

Reading code shows you what people actually do when nobody's watching. It's the difference between the menu and the kitchen.

# Are you happy with the architecture? What do you look for?
Good architecture is invisible. You know it's good when nobody's complaining.

Concrete signals:
- New features go where they obviously belong, not "somewhere"
- You can explain the system to someone in under 10 minutes
- Tests don't require a PhD to write
- Developers aren't playing "guess which service owns this logic"
- Fixing a bug doesn't cascade into 8 other bugs

Bad architecture makes easy things hard. Good architecture makes hard things possible.

# What issues are you seeing?
The usual suspects:
- God classes that do everything except make coffee (actually, they probably do that too)
- Circular dependencies that make you question your career choices
- "Change this one field" requires touching 14 files across 3 repos
- Every feature is implemented differently because "this time is special"
- Abstraction layers that abstract nothing but make everything slower
- Configuration in environment variables, config files, database, hardcoded constants, and Steve's head

If debugging is the process of removing bugs, then programming must be the process of adding them. Architecture is trying to make that process take longer.

# How can we make it better?
Start with what hurts. If nobody's complaining, maybe it's fine.

Small steps beat grand visions. Refactor the thing that's slowing you down today, not the thing that might slow you down in 2027.

Document why, not what. Code shows what. Comments and ADRs should explain why you made questionable life decisions.

Delete more than you add. Every line of code is a liability. Every abstraction is a bet. Some bets don't pay off.

Get the team on the same page before writing code, not after you've built it three different ways.

Architecture isn't something you do once. It's something you do every day, in every PR, in every design discussion. It's a practice, not a phase.