# Runbook: manual deployment to an STE stack

Use this to deploy the service by hand into one STE stack, with the shared `springboot-app` Helm
chart, before it is onboarded to Flux. The values file is
[`manual-ste-values.yaml`](manual-ste-values.yaml). Environment-specific values (hosts, namespace,
vault and secret names, identity) are **not** in this repository: the deployer exports them in their
shell from the team's private notes.

A manual release is not managed by Flux. It is lost if the namespace is rebuilt, and it should be
removed (`helm uninstall`) once the service is onboarded to Flux, so the two never compete.

## Prerequisites

1. **An image in ACR.** CI publishes an image for every push to `main` (ADO pipeline 460 pushes it to
   ACR as `hmcts/service-cp-crime-results-bulkresulting-service:<artefact version>_<YYYYMMDDHHMMSS>`).
   Find the tag to deploy from the live listing, never from notes:

   ```bash
   az acr repository show-tags -n "$ACR_NAME" \
     --repository hmcts/service-cp-crime-results-bulkresulting-service --orderby time_desc --top 5 -o table
   ```

2. **Key Vault objects.** Every object in `secretProvider` must exist in the stack's key vault, or
   the pod will not start:
   - the datasource password for the service's own database role (**new**: it must be created
     first, with any generated value; the chart's database hook creates the role with it);
   - the Postgres admin, `pgsqladmin` and read-only passwords (shared objects the sibling services
     already use).
3. **The workload identity.** The release uses the stack's shared Spring Boot identity. Read its
   client id from the `springboot-identity-settings` ConfigMap in the namespace (key `clientId`).
   It needs `KeyVaultSecretsUser` on the key vault.
4. **The stack is running.** STE namespaces are scaled to 0 overnight. Bring the stack up first and
   wait until usersgroups is ready, otherwise every authenticated call fails.
5. **Tools.** `az` logged in to the non-live subscription, `kubectl`, `helm` 3.8 or later, `envsubst`.

## Steps

1. **Check the cluster context.** Do this before any `kubectl` or `helm` command, and stop if it is
   not the STE cluster that hosts the stack:

   ```bash
   kubectl config current-context
   kubectl get namespace "$NAMESPACE"
   ```

2. **Export the deployment variables** (values from the team's private notes; the names match the
   placeholders in the values file):

   ```bash
   export NAMESPACE=...                 # the stack's CCM namespace
   export ACR_NAME=...                  # ACR name, without .azurecr.io
   export IMAGE_TAG=...                 # from the ACR listing in prerequisite 1
   export INGRESS_HOST=...              # the stack's public ingress host
   export CP_BASE_URL=...               # http://<the stack's internal ingress host>
   export DB_HOST=...                   # the stack's Postgres service
   export KEY_VAULT_NAME=...
   export DATASOURCE_PASSWORD_SECRET=...
   export PG_ADMIN_PASSWORD_SECRET=...
   export PGSQLADMIN_PASSWORD_SECRET=...
   export PG_READONLY_PASSWORD_SECRET=...
   export OIDC_ISSUER_URL=...           # the cluster's OIDC issuer
   export TENANT_ID=...
   export MANAGED_IDENTITY_CLIENT_ID="$(kubectl -n "$NAMESPACE" get configmap springboot-identity-settings \
     -o jsonpath='{.data.clientId}')"
   ```

3. **Find the chart.** The sibling services use `springboot-app` 1.2.24 from the ACR's OCI chart
   repository. Confirm the version exists:

   ```bash
   az acr login -n "$ACR_NAME"
   helm show chart "oci://$ACR_NAME.azurecr.io/charts/springboot-app" --version 1.2.24
   ```

4. **Render the values and check them.** Every placeholder must be filled:

   ```bash
   envsubst < doc/runbooks/manual-ste-values.yaml > /tmp/bulkresulting-values.yaml
   grep -n '\${' /tmp/bulkresulting-values.yaml && echo "unfilled placeholders above" || echo "all filled"
   helm template bulkresulting-service "oci://$ACR_NAME.azurecr.io/charts/springboot-app" --version 1.2.24 \
     -n "$NAMESPACE" -f /tmp/bulkresulting-values.yaml > /tmp/bulkresulting-rendered.yaml
   ```

   Read `/tmp/bulkresulting-rendered.yaml`: the VirtualService matches `/bulkresulting` and
   `/api/bulkresulting` and rewrites to `/bulkresulting`; the SecretProviderClass lists the four
   objects; the database Job creates `bulkresulting`.

5. **Install or upgrade:**

   ```bash
   helm upgrade --install bulkresulting-service "oci://$ACR_NAME.azurecr.io/charts/springboot-app" \
     --version 1.2.24 -n "$NAMESPACE" -f /tmp/bulkresulting-values.yaml --wait --timeout 10m
   ```

## Verify

```bash
kubectl -n "$NAMESPACE" get pods -l app.kubernetes.io/instance=bulkresulting-service
# The chart names the deployment <release>-springboot-app.
kubectl -n "$NAMESPACE" logs deploy/bulkresulting-service-springboot-app --tail=100   # Flyway V1 applied, started
kubectl -n "$NAMESPACE" port-forward deploy/bulkresulting-service-springboot-app 4550:4550 &
curl -s http://localhost:4550/bulkresulting/actuator/health/readiness   # {"status":"UP"}
curl -s -H "CJSCPPUID: <an STE user id>" http://localhost:4550/bulkresulting/config
curl -s -H "CJSCPPUID: <an STE user id>" \
  "http://localhost:4550/bulkresulting/hearings?ouCode=<a court the user is placed at>&startDate=<today>"
```

Then through the gateway, signed in to the STE frontend: `GET /api/bulkresulting/hearings?...`.

The test user needs a placement at the court searched, and must be in a group that listing's range
search and hearing's `get.hearings` admit (for example Legal Advisers). The interim authorisation
rule in this service admits any identified caller.

## Roll back or remove

```bash
helm history bulkresulting-service -n "$NAMESPACE"
helm rollback bulkresulting-service <revision> -n "$NAMESPACE"
helm uninstall bulkresulting-service -n "$NAMESPACE"   # the database is left in place
```
