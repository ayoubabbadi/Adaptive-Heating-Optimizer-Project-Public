# Deployment Instructions for Auto-Starting the Script

To ensure the `raspberrypi_logic.py` script runs automatically every time your Raspberry Pi boots up, we will set it up as a `systemd` service. This is the modern, standard way to manage services on Linux.

Follow these steps carefully.

### Step 1: Make the Python Script Executable

First, we need to ensure the script has the correct permissions to be executed directly.

1.  Navigate to the project directory:
    ```bash
    cd /path/to/your/project/raspberrypi_logic
    ```

2.  Make the script executable:
    ```bash
    chmod +x raspberrypi_logic.py
    ```

3.  It's also a good practice to add a "shebang" line at the very top of the Python script to tell the system which interpreter to use. Make sure the first line of `raspberrypi_logic.py` is:
    ```python
    #!/usr/bin/env python3
    ```
    *(I will add this line to the script for you in the next step to ensure it works correctly.)*

### Step 2: Create the `systemd` Service File

Next, you will create a service file that tells `systemd` how to run your script.

1.  Create a new service file using the `nano` text editor:
    ```bash
    sudo nano /etc/systemd/system/adaptive-heating.service
    ```

2.  Copy and paste the following content into the file. **Crucially, you must replace `/path/to/your/project/` with the actual, full path to where you have cloned this repository.**

    ```ini
    [Unit]
    Description=Adaptive Heating Logic Service
    After=network.target

    [Service]
    ExecStart=/usr/bin/python3 /path/to/your/project/raspberrypi_logic/raspberrypi_logic.py
    WorkingDirectory=/path/to/your/project/raspberrypi_logic/
    StandardOutput=inherit
    StandardError=inherit
    Restart=always
    User=pi

    [Install]
    WantedBy=multi-user.target
    ```

3.  Save the file and exit `nano` by pressing `Ctrl+X`, then `Y`, then `Enter`.

### Step 3: Enable and Start the Service

Now that the service file is created, you need to tell `systemd` to use it.

1.  Reload the `systemd` daemon to make it aware of the new service:
    ```bash
    sudo systemctl daemon-reload
    ```

2.  Enable the service to start on boot:
    ```bash
    sudo systemctl enable adaptive-heating.service
    ```

3.  Start the service immediately to test it:
    ```bash
    sudo systemctl start adaptive-heating.service
    ```

### Step 4: Verify the Service is Running

You can check the status of your service to see if it's running correctly.

1.  Check the status:
    ```bash
    sudo systemctl status adaptive-heating.service
    ```
    You should see output indicating that the service is "active (running)".

2.  You can also view the logs in real-time to see the script's output:
    ```bash
    journalctl -u adaptive-heating.service -f
    ```

Your script will now start automatically every time you reboot your Raspberry Pi.
