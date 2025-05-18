#!/bin/bash

# OpenNMS Cloud Bridge - Project Setup Script
# This script creates the initial project structure

set -e

echo "🚀 Setting up OpenNMS Cloud Bridge project structure..."

# Create main directories
mkdir -p backend/{app,models,services,api,config,utils}
mkdir -p frontend/{src,public,components,pages,services}
mkdir -p database/{migrations,seeds}
mkdir -p docs/{api,setup,user-guide}
mkdir -p tests/{unit,integration,e2e}
mkdir -p scripts
mkdir -p config/{templates,examples}
mkdir -p logs

# Backend structure
mkdir -p backend/app/{core,auth,cloud_providers,monitoring,opennms}
mkdir -p backend/models/{base,cloud,opennms,monitoring}
mkdir -p backend/services/{cloud,opennms,metrics,security}
mkdir -p backend/api/{v1/{endpoints,dependencies},middleware}

# Frontend structure
mkdir -p frontend/src/{components/{common,providers,monitoring},pages,services,store,styles}

# Create initial files
touch backend/app/__init__.py
touch backend/app/main.py
touch backend/models/__init__.py
touch backend/services/__init__.py
touch backend/api/__init__.py

# Configuration files
cat > config/settings.py << 'EOF'
"""
Application settings and configuration management.
"""
from pydantic_settings import BaseSettings
from typing import Optional, List
import os

class Settings(BaseSettings):
    # Application
    app_name: str = "OpenNMS Cloud Bridge"
    app_version: str = "0.1.0"
    debug: bool = False
    
    # Server
    host: str = "127.0.0.1"
    port: int = 8080
    
    # Database
    database_url: str = "sqlite:///./onmscloudbridge.db"
    
    # Security
    secret_key: str = os.urandom(32).hex()
    encryption_key: Optional[str] = None
    
    # OpenNMS
    opennms_base_url: Optional[str] = None
    opennms_username: Optional[str] = None
    opennms_password: Optional[str] = None
    
    # Monitoring
    collection_interval: int = 60  # seconds
    max_concurrent_collections: int = 10
    
    # Logging
    log_level: str = "INFO"
    log_file: str = "logs/application.log"
    
    class Config:
        env_file = ".env"
        case_sensitive = False

settings = Settings()
EOF

# Create basic main.py
cat > backend/app/main.py << 'EOF'
"""
OpenNMS Cloud Bridge - Main application entry point.
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
import uvicorn

from config.settings import settings

# Create FastAPI application
app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="Cloud infrastructure monitoring bridge to OpenNMS",
    docs_url="/api/docs",
    redoc_url="/api/redoc"
)

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://localhost:8080"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount static files (frontend)
if os.path.exists("frontend/build"):
    app.mount("/", StaticFiles(directory="frontend/build", html=True), name="static")

@app.get("/api/health")
async def health_check():
    """Health check endpoint."""
    return {
        "status": "healthy",
        "version": settings.app_version,
        "app": settings.app_name
    }

@app.get("/api/info")
async def app_info():
    """Application information endpoint."""
    return {
        "name": settings.app_name,
        "version": settings.app_version,
        "debug": settings.debug
    }

if __name__ == "__main__":
    print(f"🚀 Starting {settings.app_name} v{settings.app_version}")
    uvicorn.run(
        "backend.app.main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        log_level=settings.log_level.lower()
    )
EOF

# Create a simple docker-compose for development
cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - DATABASE_URL=sqlite:///./onmscloudbridge.db
      - DEBUG=true
    volumes:
      - .:/app
      - ./logs:/app/logs
      - ./data:/app/data
    depends_on:
      - redis

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

  # Optional: PostgreSQL for production
  # postgres:
  #   image: postgres:15
  #   environment:
  #     POSTGRES_DB: onmscloudbridge
  #     POSTGRES_USER: app
  #     POSTGRES_PASSWORD: password
  #   volumes:
  #     - postgres_data:/var/lib/postgresql/data
  #   ports:
  #     - "5432:5432"

volumes:
  redis_data:
  # postgres_data:
EOF

# Create basic Dockerfile
cat > Dockerfile << 'EOF'
FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    build-essential \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements first for better caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create logs directory
RUN mkdir -p logs

# Expose port
EXPOSE 8080

# Run the application
CMD ["python", "backend/app/main.py"]
EOF

# Create development scripts
cat > scripts/dev-setup.sh << 'EOF'
#!/bin/bash
echo "🔧 Setting up development environment..."

# Create virtual environment
python -m venv venv
source venv/bin/activate

# Install dependencies
pip install -r requirements.txt

# Create .env file if it doesn't exist
if [ ! -f .env ]; then
    cat > .env << 'ENVEOF'
# Development environment variables
DEBUG=true
SECRET_KEY=your-secret-key-here
DATABASE_URL=sqlite:///./onmscloudbridge.db
LOG_LEVEL=DEBUG
ENVEOF
    echo "📝 Created .env file with default values"
fi

# Initialize database
mkdir -p data
touch data/onmscloudbridge.db

echo "✅ Development environment ready!"
echo "Run: python backend/app/main.py"
EOF

cat > scripts/run-dev.sh << 'EOF'
#!/bin/bash
echo "🚀 Starting OpenNMS Cloud Bridge in development mode..."

# Activate virtual environment if it exists
if [ -d "venv" ]; then
    source venv/bin/activate
fi

# Start the application with auto-reload
python backend/app/main.py
EOF

# Make scripts executable
chmod +x scripts/*.sh

# Create basic test structure
cat > tests/conftest.py << 'EOF'
"""Test configuration and fixtures."""
import pytest
from fastapi.testclient import TestClient
from backend.app.main import app

@pytest.fixture
def client():
    """Test client fixture."""
    return TestClient(app)

@pytest.fixture
def test_settings():
    """Test settings fixture."""
    from config.settings import Settings
    return Settings(
        database_url="sqlite:///./test.db",
        debug=True
    )
EOF

cat > tests/test_main.py << 'EOF'
"""Test main application endpoints."""

def test_health_check(client):
    """Test health check endpoint."""
    response = client.get("/api/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"

def test_app_info(client):
    """Test app info endpoint."""
    response = client.get("/api/info")
    assert response.status_code == 200
    data = response.json()
    assert "name" in data
    assert "version" in data
EOF

# Create documentation structure
cat > docs/setup.md << 'EOF'
# Setup Guide

## Development Environment

1. Clone the repository
2. Run setup script: `./scripts/dev-setup.sh`
3. Start development server: `./scripts/run-dev.sh`

## Production Deployment

1. Build Docker image: `docker build -t onmscloudbridge .`
2. Run with Docker Compose: `docker-compose up -d`

## Configuration

See `config/settings.py` for all configuration options.
Environment variables can be set in `.env` file.
EOF

# Create initial frontend structure (placeholder)
cat > frontend/package.json << 'EOF'
{
  "name": "onmscloudbridge-frontend",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "next lint"
  },
  "dependencies": {
    "react": "^18.2.0",
    "react-dom": "^18.2.0",
    "next": "^14.0.0"
  }
}
EOF

echo ""
echo "✅ Project structure created successfully!"
echo ""
echo "Next steps:"
echo "1. Run: ./scripts/dev-setup.sh"
echo "2. Configure your .env file"
echo "3. Start development: ./scripts/run-dev.sh"
echo ""
echo "Project structure:"
tree -I '__pycache__|node_modules|*.pyc' -a -L 3

echo ""
echo "🎉 OpenNMS Cloud Bridge is ready for development!"