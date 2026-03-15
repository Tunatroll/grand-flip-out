# Originality and attribution

## This plugin is original work

Grand Flip Out is written from scratch. No code has been copied from any other RuneLite plugins or third‑party GE/market tools. All plugin logic, UI, tracking, and API integration are original implementations.

## How the project was started

The project was initialized using the **official RuneLite plugin example** (the public template repository that RuneLite provides for building plugins). That template gives you:

- A standard Gradle build and project layout
- A minimal example plugin class and config
- The correct way to depend on the RuneLite client

Using that template is the normal and intended way to start a RuneLite plugin. The template code was only a starting scaffold; it has been fully replaced with our own package names, classes, and logic. No code from any other plugin (including any other GE or market plugins) is included in this repository.

## What we use

- **RuneLite client API** — We depend on the official RuneLite client as a library (`net.runelite:client`) to implement a plugin. That is standard plugin development and is not copying another plugin’s code.
- **Lombok, Gson** — Standard Java libraries used in a normal way.
- **Our code** — Everything under `com.grandflipout` (plugin, config, network, tracking, UI) is original code written for Grand Flip Out.

## Summary

- **Template:** Started from the official RuneLite plugin example; that scaffold has been fully replaced.
- **Third‑party plugins:** No code from any other RuneLite plugins or third‑party projects is used.
- **Grand Flip Out:** All application code in this repo is original.
