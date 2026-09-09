# Metadata API

All of it is optional. Every result, status, duration, error and retry is reported without
a single call here — this adds only what JUnit has no concept of.

Register the extension first, by annotation or auto-detection. See
[CONFIGURATION.md](./CONFIGURATION.md#turning-the-metadata-api-on).

## Guarantees

- **Nothing here can fail your test.** No method returns an error, none throws, and every
  call is inert when no reporter is listening.
- **Calls outside a registered test are dropped with a warning**, not guessed at. Attaching
  them to whichever test runs next is silent wrong data, which is worse than absent data.
- **Warnings are emitted once per JVM**, not once per call, so a loop cannot drown the log.

## Reference

| Call | Effect |
|---|---|
| `Qualflare.label(name, value)` | A named dimension to group and filter by |
| `Qualflare.tag(tags...)` | One or more free tags; empty and null are ignored |
| `Qualflare.link(url)` | A link of type `custom` |
| `Qualflare.link(url, type, name)` | `Qualflare.ISSUE`, `Qualflare.TMS` or `Qualflare.CUSTOM` |
| `Qualflare.priority(p)` | `Qualflare.HIGH`, `MEDIUM` or `LOW` |
| `Qualflare.description(text)` | Free text shown on the case |
| `Qualflare.parameter(name, value)` | A recorded input |
| `Qualflare.maskedParameter(name)` | A recorded input whose value is **never sent** |
| `Qualflare.step(name, body)` | A timed step; steps nest |

## Steps

```java
Qualflare.step("add to cart", () -> {
    Qualflare.parameter("sku", "widget");
    Qualflare.step("set quantity", () -> Qualflare.parameter("qty", "2"));
});
```

Timing is real elapsed time around the body. A parameter emitted inside an open step
belongs to that step; one emitted outside belongs to the case.

A step whose body throws is recorded as `failed` **and the exception still propagates**.
Swallowing it would turn a failing test green, which is the worst thing a reporter can do.

## Masked parameters take no value

```java
Qualflare.maskedParameter("token");   // there is no overload that accepts a value
```

`masked` is a display hint the server does not act on, so withholding the value here is the
only thing that actually keeps a secret out of the report. A signature that cannot accept
one cannot leak one.

## How it travels

Calls are published as JUnit report entries under the `qf.` namespace, which the listener
consumes. Entries your project publishes for its own reasons pass through untouched — they
are not ours to swallow.

Ordering is what makes nesting work: there are no step ids, so a start/stop stack
reconstructs the tree from emission order alone.
