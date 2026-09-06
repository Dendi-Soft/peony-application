# peony-application
Bankend service

### Installing and running the app

To install and run the app first clone the repository and copy git hooks from hooks/ to .git/hooks/ (manual hook instalation is necesary since git cli does not commit any files from the .git/ directory) and then start docker commpose

```sh
git clone git@github.com:Dendi-Soft/peony-application.git
cd peony-application
cp -r hooks/ .git/
docker compose watch
```
