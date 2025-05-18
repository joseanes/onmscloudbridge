# OpenNMS Cloud Bridge

A cloud infrastructure monitoring solution that collects Virtual Machine statistics from multiple cloud providers (AWS, Azure, Google Cloud) and publishes them to OpenNMS for analysis and long-term storage.

## Overview

OpenNMS Cloud Bridge enables seamless integration between cloud infrastructure and OpenNMS monitoring. It automatically discovers virtual machines across cloud providers, collects performance metrics, and forwards them to OpenNMS while handling location-based routing to avoid IP address conflicts.

## Features

- **Multi-Cloud Support**: AWS, Azure, and Google Cloud Platform
- **Secure Credential Management**: Encrypted storage of cloud and OpenNMS credentials
- **Automatic VM Discovery**: Discovers and catalogs virtual machines across providers
- **Location-Aware Routing**: Handles IP conflicts using OpenNMS locations
- **Real-time Metrics Collection**: CPU, memory, and network statistics
- **Error Recovery**: Intelligent error handling with user-friendly reporting
- **Web-based Configuration**: Easy-to-use interface for setup and management
- **Background Processing**: Runs continuously without UI interaction

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Cloud APIs    │    │  Cloud Bridge   │    │    OpenNMS      │
│                 │    │                 │    │                 │
│  ┌───┐ ┌───┐   │    │  ┌───────────┐  │    │  ┌───────────┐  │
│  │AWS│ │GCP│   │◄───┤  │ Collector │  │────►│  │   Nodes   │  │
│  └───┘ └───┘   │    │  └───────────┘  │    │  └───────────┘  │
│  ┌───┐         │    │  ┌───────────┐  │    │  ┌───────────┐  │
│  │AZ │         │    │  │  Config   │  │    │  │ Locations │  │
│  └───┘         │    │  └───────────┘  │    │  └───────────┘  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Quick Start

### Prerequisites

- Python 3.8+ or Node.js 16+
- Access to cloud provider accounts (AWS, Azure, GCP)
- OpenNMS instance with REST API access
- SQLite (included) or PostgreSQL (optional)

### Installation

```bash
# Clone the repository
git clone https://github.com/joseanes/onmscloudbridge.git
cd onmscloudbridge

# Install dependencies
pip install -r requirements.txt

# Run initial setup
python setup.py

# Start the application
python main.py
```

### Configuration

1. **Access the web interface**: http://localhost:8080
2. **Add cloud providers**: Configure credentials and regions
3. **Configure OpenNMS**: Set endpoint and authentication
4. **Discover VMs**: Scan for virtual machines
5. **Enable monitoring**: Select VMs to monitor
6. **Start collection**: Begin automatic metric collection

## Configuration

### Cloud Providers

#### AWS
- IAM user with `CloudWatchReadOnlyAccess` and `AmazonEC2ReadOnlyAccess` permissions
- Access Key ID and Secret Access Key

#### Azure
- Service Principal with Reader role
- Tenant ID, Client ID, and Client Secret

#### Google Cloud
- Service Account with Compute Viewer and Monitoring Viewer roles
- JSON key file

### OpenNMS
- OpenNMS base URL (e.g., http://opennms.example.com:8980/opennms)
- Username and password with administrative privileges
- REST API enabled

## Development

### Project Structure

```
onmscloudbridge/
├── backend/           # Core application logic
├── frontend/          # Web configuration interface
├── database/          # Schema and migrations
├── tests/            # Unit and integration tests
├── docs/             # Documentation
└── scripts/          # Utility scripts
```

### Development Setup

```bash
# Install development dependencies
pip install -r requirements-dev.txt

# Run tests
pytest

# Start development server
python -m uvicorn backend.main:app --reload
```

## API Documentation

API documentation is available at `/docs` when the application is running.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Security

- All credentials are encrypted at rest using AES-256
- HTTPS/TLS for all API communications
- No sensitive data in logs or error messages
- Regular security dependency updates

## Troubleshooting

### Common Issues

**Connection Failures**
- Verify cloud provider credentials
- Check network connectivity and firewalls
- Ensure OpenNMS REST API is accessible

**Missing Metrics**
- Confirm CloudWatch/Azure Monitor permissions
- Check VM instance states
- Verify metric collection intervals

**OpenNMS Integration**
- Validate OpenNMS credentials
- Ensure locations are properly configured
- Check for IP address conflicts

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For issues and questions:
- Create an [Issue](https://github.com/joseanes/onmscloudbridge/issues)
- Check the [Documentation](docs/)
- Join our [Discussions](https://github.com/joseanes/onmscloudbridge/discussions)

## Roadmap

- [ ] Initial MVP implementation
- [ ] AWS integration
- [ ] OpenNMS basic integration
- [ ] Web configuration interface
- [ ] Azure support
- [ ] Google Cloud support
- [ ] Advanced metrics collection
- [ ] Alerting and notifications
- [ ] High availability deployment
- [ ] Kubernetes operator

---

**Project Status**: 🚧 Under Development