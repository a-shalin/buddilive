### Docker Deployment

**Docker Compose services**:
- `nginx` (:80/:443) - Reverse proxy with Let's Encrypt SSL
- `app` (:8686) - BuddiLive application
- `postgres` (:5432) - PostgreSQL 16

### Ansible Automation

```
ansible/
├── inventory/production.ini   # Server: ora.cloudinfosys.ru
├── playbooks/deploy.yml       # Full deployment playbook
├── templates/
│   ├── docker-compose.yml.j2  # Compose template
│   ├── nginx.conf.j2          # Nginx config
│   └── nginx-ssl.conf.j2      # SSL config
```

Deployment flow: Git clone → Docker build → Docker Compose up → Health checks
