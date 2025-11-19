$plugins = @(
    "primeleague-core",
    "primeleague-clans",
    "primeleague-economy",
    "primeleague-league",
    "primeleague-gladiador",
    "primeleague-x1",
    "primeleague-elo",
    "primeleague-stats",
    "primeleague-auth",
    "primeleague-chat",
    "primeleague-discord",
    "primeleague-payment",
    "primeleague-punishments"
)

$serverPluginsDir = (Resolve-Path "../server/plugins").Path
Write-Host "Deploying to: $serverPluginsDir" -ForegroundColor Cyan

# Ensure server plugins directory exists
if (!(Test-Path $serverPluginsDir)) {
    New-Item -ItemType Directory -Force -Path $serverPluginsDir | Out-Null
    Write-Host "Created directory: $serverPluginsDir" -ForegroundColor Green
}

foreach ($plugin in $plugins) {
    Write-Host "Building $plugin..." -ForegroundColor Cyan
    Push-Location "plugins/$plugin"
    
    # Run Maven build
    cmd /c "mvn clean package -DskipTests"
    
    if ($LASTEXITCODE -eq 0) {
        # Find the jar (excluding shaded/original if possible, or just taking the main one)
        $jarFile = Get-ChildItem "target/*.jar" | Where-Object { $_.Name -notlike "original-*" -and $_.Name -notlike "*-shaded.jar" } | Select-Object -First 1
        
        if ($jarFile) {
            Copy-Item $jarFile.FullName -Destination $serverPluginsDir -Force
            Write-Host "Deployed $($jarFile.Name) to server/plugins" -ForegroundColor Green
        } else {
            # Fallback for shaded jars if that's the only option
             $shadedJar = Get-ChildItem "target/*-shaded.jar" | Select-Object -First 1
             if ($shadedJar) {
                Copy-Item $shadedJar.FullName -Destination $serverPluginsDir -Force
                Write-Host "Deployed $($shadedJar.Name) to server/plugins" -ForegroundColor Green
             } else {
                Write-Host "No JAR found for $plugin" -ForegroundColor Red
             }
        }
    } else {
        Write-Host "Build failed for $plugin" -ForegroundColor Red
    }
    
    Pop-Location
}

Write-Host "Build and deploy process completed." -ForegroundColor Magenta
