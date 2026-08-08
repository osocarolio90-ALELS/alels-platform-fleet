# P3 Autoscaling

These policies scale stateless gateway and ingestion replicas inside one ALELS cell. They require a Kubernetes metrics adapter that exposes the named Prometheus/Kafka metrics as External Metrics. Apply them only after the corresponding `alels-gateway` and `alels-ingestion` Deployments exist in the cell namespace.

Gateway scaling uses active connections per pod and keeps capacity below the 70% certification ceiling. Ingestion scaling uses consumer lag. Scale-down is deliberately slow to avoid connection churn and Kafka rebalance storms. A gateway Deployment must drain readiness before termination and must configure distributed session ownership whenever replicas exceed one.

These manifests are production templates, not evidence of capacity certification. Validate metric availability, disruption budgets, node headroom and failure-domain placement in the target cluster before applying them.
