@echo off
echo Requesting administrative privileges...
:: Check for admin rights
net session >nul 2>&1
if %errorLevel% == 0 (
    echo Administrator rights confirmed.
    
    echo Adding Firewall Rule for VoxPop WebSockets (TCP 8887)...
    netsh advfirewall firewall add rule name="VoxPop - WebSockets (TCP 8887)" dir=in action=allow protocol=TCP localport=8887
    
    echo Adding Firewall Rule for VoxPop mDNS (UDP 5353)...
    netsh advfirewall firewall add rule name="VoxPop - mDNS (UDP 5353)" dir=in action=allow protocol=UDP localport=5353
    
    echo Firewall rules applied successfully.
    pause
) else (
    echo Failure: You must run this script as Administrator.
    pause
)