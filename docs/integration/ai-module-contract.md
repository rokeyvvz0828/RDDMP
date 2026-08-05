# AI Module Contract

Modules request a named capability, not a provider-specific model. The server chooses the enabled route by priority, validates that the model belongs to the current tenant, and records an execution audit row. Provider credentials remain server-side and are never returned by provider/model APIs, logs, or execution responses. Provider adapters must implement a server-side boundary and may not execute arbitrary browser-provided code.

Configuration endpoints: GET|POST /api/ai/providers, GET|POST /api/ai/models, and GET|POST /api/ai/routes.
Execution endpoint: POST /api/ai/execute.

A capability request contains capability and optional input. Modules must not send provider-specific credentials or execute arbitrary model code from the browser.