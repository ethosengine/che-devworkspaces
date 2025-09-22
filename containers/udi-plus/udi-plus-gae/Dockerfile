# Legacy Google App Engine Python 2.7 Development Container
# Layered on top of the udi-plus base image with Java 21 and Claude Code
FROM harbor.ethosengine.com/devspaces/udi-plus:latest

USER root

# Install minimal build dependencies for Python 2.7
# Using only packages that are definitely available in UBI9
RUN dnf update -y && \
    dnf install -y \
    gcc \
    gcc-c++ \
    make \
    openssl-devel \
    libffi-devel \
    zlib-devel \
    sqlite-devel \
    bzip2-devel \
    ncurses-devel \
    xz-devel \
    expat-devel \
    wget \
    tar \
    && dnf clean all

# Install Python 2.7 from source since it's not available in UBI9 repos
# Skip test suite to avoid SSL test failures and speed up build
WORKDIR /tmp
RUN wget https://www.python.org/ftp/python/2.7.18/Python-2.7.18.tgz && \
    tar xzf Python-2.7.18.tgz && \
    cd Python-2.7.18 && \
    ./configure --prefix=/usr/local --enable-shared && \
    make -j$(nproc) && \
    make altinstall && \
    cd / && \
    rm -rf /tmp/Python-2.7.18*

# Create symlinks for python2 commands
RUN ln -sf /usr/local/bin/python2.7 /usr/local/bin/python2 && \
    ln -sf /usr/local/bin/python2.7 /usr/local/bin/python-legacy

# Update library path for Python 2.7 shared libraries
RUN echo "/usr/local/lib" > /etc/ld.so.conf.d/python27.conf && \
    ldconfig

# Install pip for Python 2.7
RUN wget https://bootstrap.pypa.io/pip/2.7/get-pip.py && \
    /usr/local/bin/python2.7 get-pip.py && \
    rm get-pip.py

# Download and install Google App Engine SDK for Python 2.7
# Using the last stable version that supported Python 2.7
WORKDIR /opt
RUN wget -q https://storage.googleapis.com/appengine-sdks/featured/google_appengine_1.9.91.zip && \
    unzip -q google_appengine_1.9.91.zip && \
    rm google_appengine_1.9.91.zip && \
    chmod -R 755 /opt/google_appengine

# Install common legacy Python packages that Khan Academy dependencies require
# Using older versions that are more compatible with Python 2.7 and don't require Cython
RUN /usr/local/bin/python2.7 -m pip install \
    webapp2==2.5.2 \
    jinja2==2.10.1 \
    webob==1.8.7 \
    pyyaml==3.13 \
    requests==2.25.1 \
    markupsafe==1.1.1 \
    six==1.16.0

# Install packages that require compilation separately with fallbacks
RUN /usr/local/bin/python2.7 -m pip install lxml==4.2.6 || \
    /usr/local/bin/python2.7 -m pip install lxml==3.8.0 || \
    echo "lxml installation failed - skipping"

RUN /usr/local/bin/python2.7 -m pip install pillow==6.2.2 || \
    /usr/local/bin/python2.7 -m pip install pillow==5.4.1 || \
    echo "pillow installation failed - skipping"

# Create helpful scripts for GAE development
RUN echo '#!/bin/bash' > /usr/local/bin/gae-dev-server && \
    echo 'export PATH="/opt/google_appengine:$PATH"' >> /usr/local/bin/gae-dev-server && \
    echo 'export PYTHONPATH="/opt/google_appengine:$PYTHONPATH"' >> /usr/local/bin/gae-dev-server && \
    echo '/usr/local/bin/python2.7 /opt/google_appengine/dev_appserver.py "$@"' >> /usr/local/bin/gae-dev-server && \
    chmod +x /usr/local/bin/gae-dev-server

# Create a script to check GAE environment
RUN echo '#!/bin/bash' > /usr/local/bin/gae-check && \
    echo 'echo "=== GAE Environment Check ==="' >> /usr/local/bin/gae-check && \
    echo 'export LD_LIBRARY_PATH="/usr/local/lib:$LD_LIBRARY_PATH"' >> /usr/local/bin/gae-check && \
    echo 'if [ -f "/usr/local/bin/python2.7" ]; then' >> /usr/local/bin/gae-check && \
    echo '  echo "Python 2.7: $(/usr/local/bin/python2.7 --version 2>&1)"' >> /usr/local/bin/gae-check && \
    echo '  echo "Python 2.7 location: $(/usr/local/bin/python2.7 -c \"import sys; print(sys.executable)\" 2>/dev/null || echo \"Python test failed\")"' >> /usr/local/bin/gae-check && \
    echo 'else' >> /usr/local/bin/gae-check && \
    echo '  echo "Python 2.7: NOT FOUND"' >> /usr/local/bin/gae-check && \
    echo 'fi' >> /usr/local/bin/gae-check && \
    echo 'echo "GAE SDK: /opt/google_appengine"' >> /usr/local/bin/gae-check && \
    echo 'if [ -f "/opt/google_appengine/dev_appserver.py" ]; then' >> /usr/local/bin/gae-check && \
    echo '  echo "GAE dev_appserver: Found"' >> /usr/local/bin/gae-check && \
    echo 'else' >> /usr/local/bin/gae-check && \
    echo '  echo "GAE dev_appserver: NOT FOUND"' >> /usr/local/bin/gae-check && \
    echo 'fi' >> /usr/local/bin/gae-check && \
    echo 'echo "Installed legacy packages:"' >> /usr/local/bin/gae-check && \
    echo '/usr/local/bin/python2.7 -m pip list 2>/dev/null | grep -E "(webapp2|jinja2|webob|yaml)" || echo "Package check failed"' >> /usr/local/bin/gae-check && \
    chmod +x /usr/local/bin/gae-check

# Update library path for Python 2.7 shared libraries (do this as root before switching users)
RUN echo "/usr/local/lib" > /etc/ld.so.conf.d/python27.conf && \
    ldconfig

# Switch back to the user account (inheriting from udi-plus)
USER user

# Test Python 2.7 installation works
RUN /usr/local/bin/python2.7 --version && \
    /usr/local/bin/python2.7 -c "import sys; print('Python 2.7 working: ' + sys.version)"

# Add GAE environment variables and aliases to user profile
# This supplements the existing environment from udi-plus
RUN echo '' >> ~/.bashrc && \
    echo '# Legacy GAE Python 2.7 Environment' >> ~/.bashrc && \
    echo 'export GAE_SDK_ROOT=/opt/google_appengine' >> ~/.bashrc && \
    echo 'export PATH="/opt/google_appengine:/usr/local/bin:${PATH}"' >> ~/.bashrc && \
    echo 'export PYTHONPATH="/opt/google_appengine:${PYTHONPATH}"' >> ~/.bashrc && \
    echo 'export LD_LIBRARY_PATH="/usr/local/lib:${LD_LIBRARY_PATH}"' >> ~/.bashrc && \
    echo '' >> ~/.bashrc && \
    echo '# GAE Development Aliases' >> ~/.bashrc && \
    echo 'alias python2="/usr/local/bin/python2.7"' >> ~/.bashrc && \
    echo 'alias python-legacy="/usr/local/bin/python2.7"' >> ~/.bashrc && \
    echo 'alias gae-server="gae-dev-server"' >> ~/.bashrc && \
    echo 'alias gae-env="gae-check"' >> ~/.bashrc && \
    echo 'alias dev_appserver="gae-dev-server"' >> ~/.bashrc

# Test that helper scripts work
RUN gae-check || echo "GAE check had issues but continuing..."

# Set working directory to projects (standard for devfile)
WORKDIR /projects

# Expose GAE development server ports
EXPOSE 8080 8000 9000

# Set environment variables for Python 2.7 shared libraries at the container level
ENV LD_LIBRARY_PATH="/usr/local/lib:${LD_LIBRARY_PATH}"
ENV PATH="/opt/google_appengine:/usr/local/bin:${PATH}"
ENV PYTHONPATH="/opt/google_appengine:${PYTHONPATH}"

# Don't override CMD - inherit whatever udi-plus uses for Eclipse Che compatibility