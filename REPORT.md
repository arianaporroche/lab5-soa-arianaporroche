# Lab 5 Integration and SOA - Project Report

## 1. EIP Diagram (Before)

![Before Diagram](diagrams/before.png)

The initial code had several problems:
- The `evenChannel` was defined as publish/subscribe, but the channel that actually requires publish/subscribe semantics is `oddChannel`.
- The `discarded` flow was isolated and not connected to any channel, so discarded messages would never reach it.
- The filter in `oddFlow` was not discarding any messages.
- The routing in the main flow (`myFlow`) was unclear and did not properly direct messages to the intended channels.


![After Diagram](diagrams/after.png)

---

## 2. What Was Wrong

Explain the bugs you found in the starter code:

- **Bug 1**: What was the problem? Why did it happen? How did you fix it?
- **Bug 2**: What was the second problem? Why did it happen? How did you fix it?
- **Bug 3**: What was the third problem? Why did it happen? How did you fix it?
- **(More bugs if you found them)**

---

## 3. What You Learned

Write a few sentences about:

- What you learned about Enterprise Integration Patterns
- How Spring Integration works
- What was challenging and how you solved it

---

## 4. AI Disclosure

**Did you use AI tools?** (ChatGPT, Copilot, Claude, etc.)

- If YES: Which tools? What did they help with? What did you do yourself?
- If NO: Write "No AI tools were used."

**Important**: Explain your own understanding of the code and patterns, even if AI helped you write it.

---

## Additional Notes

Any other comments or observations about the assignment.
