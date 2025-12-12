package com.example.carrito

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.carrito.ui.theme.CarritoTheme
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// --- Rutas de Navegación ---
const val ROUTE_DEBUGGER = "debugger"
const val ROUTE_CONTROL = "control_carro"
const val ROUTE_PROGRAMMING = "programming_controls"

private val UART_SERVICE_UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
private val UART_CHARACTERISTIC_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var uartCharacteristic: BluetoothGattCharacteristic? = null

    private val deviceAddress = "F4:45:F4:1D:B4:88"

    private val log = mutableStateOf("Bienvenido! Conecta el dispositivo.\n")
    private val isConnected = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms[Manifest.permission.BLUETOOTH_CONNECT] == true && perms[Manifest.permission.BLUETOOTH_SCAN] == true) {
            log.value += "Permisos concedidos. Puedes conectar.\n"
        } else {
            log.value += "Permisos denegados. No se puede usar Bluetooth.\n"
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                isConnected.value = true
                log.value += "Conectado. Descubriendo servicios...\n"
                try { gatt.discoverServices() } catch (e: SecurityException) { log.value += "Error de permisos: ${e.message}\n" }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected.value = false
                log.value += "Desconectado.\n"
                try { bluetoothGatt?.close() } catch (e: SecurityException) { log.value += "Error de permisos al cerrar: ${e.message}\n" }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UART_SERVICE_UUID)
                if (service != null) {
                    uartCharacteristic = service.getCharacteristic(UART_CHARACTERISTIC_UUID)
                    if (uartCharacteristic != null) { log.value += "¡Listo para la acción! Característica encontrada.\n" }
                    else { log.value += "Error: Característica UART no encontrada.\n" }
                } else { log.value += "Error: Servicio UART no encontrado.\n" }
            } else { log.value += "Error al descubrir servicios: $status\n" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarritoTheme {
                AppNavigation(
                    log = log.value, isConnected = isConnected.value, onConnect = { connectToDevice() },
                    onDisconnect = { disconnectFromDevice() }, onSendData = { data -> sendData(data) }, onClearLog = { log.value = "" }
                )
            }
        }
    }

    private fun connectToDevice() {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) { log.value += "Error: Bluetooth no disponible o desactivado.\n"; return }
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) } else { emptyArray() }
        val allPermissionsGranted = requiredPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

        if (!allPermissionsGranted) { log.value += "Solicitando permisos...\n"; requestPermissionLauncher.launch(requiredPermissions); return }

        try {
            val device: BluetoothDevice? = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            if (device == null) { log.value += "Dispositivo no encontrado.\n"; return }
            log.value += "Conectando...\n"
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        } catch (e: SecurityException) { log.value += "Error de permisos al conectar: ${e.message}\n" }
    }

    private fun disconnectFromDevice() {
        try { bluetoothGatt?.disconnect() } catch (e: SecurityException) { log.value += "Error de permisos al desconectar: ${e.message}\n" }
    }

    private fun sendData(data: String) {
        if (!isConnected.value || uartCharacteristic == null) { 
            log.value += "No se puede enviar. No hay conexión.\n"
            return 
        }
        try {
            uartCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            uartCharacteristic?.setValue(data.toByteArray())
            bluetoothGatt?.writeCharacteristic(uartCharacteristic!!)
            log.value += "Enviado: $data\n"
        } catch (e: SecurityException) { log.value += "Error de permisos al enviar: ${e.message}\n" }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(log: String, isConnected: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit, onSendData: (String) -> Unit, onClearLog: () -> Unit) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet {
            Spacer(Modifier.height(12.dp))
            NavigationDrawerItem(label = { Text("Debugger") }, selected = currentRoute == ROUTE_DEBUGGER, onClick = { navController.navigate(ROUTE_DEBUGGER); scope.launch { drawerState.close() } })
            NavigationDrawerItem(label = { Text("Control del Carro") }, selected = currentRoute == ROUTE_CONTROL, onClick = { navController.navigate(ROUTE_CONTROL); scope.launch { drawerState.close() } })
            NavigationDrawerItem(label = { Text("Controles de Programación") }, selected = currentRoute == ROUTE_PROGRAMMING, onClick = { navController.navigate(ROUTE_PROGRAMMING); scope.launch { drawerState.close() } })
        }
    }) {
        Scaffold(topBar = {
            TopAppBar(title = { 
                val title = when (currentRoute) {
                    ROUTE_CONTROL -> "Control del Carro"
                    ROUTE_PROGRAMMING -> "Controles de Programación"
                    else -> "Debugger"
                }
                Text(title) 
            }, navigationIcon = {
                IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, contentDescription = "Menu") }
            })
        }) {
            NavHost(navController = navController, startDestination = ROUTE_DEBUGGER, modifier = Modifier.padding(it)) {
                composable(ROUTE_DEBUGGER) { DebuggerScreen(log, isConnected, onConnect, onDisconnect, onSendData, onClearLog) }
                composable(ROUTE_CONTROL) { ControlCarroScreen(onSendData = onSendData, isConnected = isConnected) }
                composable(ROUTE_PROGRAMMING) { ProgrammingControlsScreen(onSendData = onSendData, isConnected = isConnected) }
            }
        }
    }
}

@Composable
fun ProgrammingControlsScreen(onSendData: (String) -> Unit, isConnected: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Movimiento Principal", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CommandButton(icon = Icons.Default.ArrowUpward, ascii = "A", isConnected = isConnected, onClick = { repeat(4) { onSendData("A") } })
            Row {
                CommandButton(icon = Icons.Default.ArrowBack, ascii = "E", isConnected = isConnected, onClick = { repeat(4) { onSendData("E") } })
                CommandButton(icon = Icons.Default.Stop, ascii = "C", isConnected = isConnected, isStopButton = true, onClick = { repeat(4) { onSendData("C") } })
                CommandButton(icon = Icons.Default.ArrowForward, ascii = "D", isConnected = isConnected, onClick = { repeat(4) { onSendData("D") } })
            }
            CommandButton(icon = Icons.Default.ArrowDownward, ascii = "B", isConnected = isConnected, onClick = { repeat(4) { onSendData("B") } })
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        Text("Control de Velocidad", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { repeat(4) { onSendData("J") } }, enabled = isConnected) { Text("Baja (J)") }
            Button(onClick = { repeat(4) { onSendData("K") } }, enabled = isConnected) { Text("Media (K)") }
            Button(onClick = { repeat(4) { onSendData("L") } }, enabled = isConnected) { Text("Alta (L)") }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text("Movimiento Diagonal", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row {
                CommandButton(icon = Icons.Default.NorthWest, ascii = "G", isConnected = isConnected, onClick = { repeat(4) { onSendData("G") } })
                Spacer(modifier = Modifier.width(96.dp)) // Spacer to separate buttons
                CommandButton(icon = Icons.Default.NorthEast, ascii = "F", isConnected = isConnected, onClick = { repeat(4) { onSendData("F") } })
            }
            Row {
                CommandButton(icon = Icons.Default.SouthWest, ascii = "H", isConnected = isConnected, onClick = { repeat(4) { onSendData("H") } })
                Spacer(modifier = Modifier.width(96.dp))
                CommandButton(icon = Icons.Default.SouthEast, ascii = "I", isConnected = isConnected, onClick = { repeat(4) { onSendData("I") } })
            }
        }
    }
}

@Composable
private fun CommandButton(icon: ImageVector, ascii: String, isConnected: Boolean, onClick: () -> Unit, isStopButton: Boolean = false) {
    Button(
        onClick = onClick, enabled = isConnected, modifier = Modifier.size(96.dp),
        shape = CircleShape, colors = if (isStopButton) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = ascii, modifier = Modifier.size(40.dp))
            Text(text = "($ascii)")
        }
    }
}


@Composable
fun ControlCarroScreen(onSendData: (String) -> Unit, isConnected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Joystick(enabled = isConnected) {
                when (it) {
                    JoystickDirection.UP -> repeat(4) { onSendData("A") }
                    JoystickDirection.DOWN -> repeat(4) { onSendData("B") }
                    JoystickDirection.STOPPED -> repeat(4) { onSendData("C") }
                    JoystickDirection.RIGHT -> repeat(4) { onSendData("D") }
                    JoystickDirection.LEFT -> repeat(4) { onSendData("E") }
                    JoystickDirection.UP_RIGHT -> repeat(4) { onSendData("F") }
                    JoystickDirection.UP_LEFT -> repeat(4) { onSendData("G") }
                    JoystickDirection.DOWN_LEFT -> repeat(4) { onSendData("H") }
                    JoystickDirection.DOWN_RIGHT -> repeat(4) { onSendData("I") }
                }
            }
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = { repeat(4) { onSendData("C") } }, enabled = isConnected, shape = CircleShape,
                modifier = Modifier.size(100.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("PARAR") }
        }
        VelocitySlider(onSendData = onSendData, isConnected = isConnected)
    }
}

@Composable
fun VelocitySlider(onSendData: (String) -> Unit, isConnected: Boolean) {
    var sliderPosition by remember { mutableFloatStateOf(1f) } // 0=Baja, 1=Media, 2=Alta
    var lastSentLevel by remember { mutableIntStateOf(1) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.height(300.dp)
    ) {
        Text("Alta")
        Slider(
            value = sliderPosition,
            onValueChange = { sliderPosition = it },
            onValueChangeFinished = {
                val currentLevel = sliderPosition.toInt()
                if (currentLevel != lastSentLevel) {
                    val speedChar = when (currentLevel) {
                        2 -> "L" // Alta
                        1 -> "K" // Media
                        else -> "J" // Baja
                    }
                    repeat(4) { onSendData(speedChar) }
                    lastSentLevel = currentLevel
                }
            },
            modifier = Modifier
                .weight(1f)
                .rotate(-90f),
            valueRange = 0f..2f,
            steps = 1,
            enabled = isConnected
        )
        Text("Baja")
    }
}

enum class JoystickDirection { UP, DOWN, LEFT, RIGHT, UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT, STOPPED }

@Composable
fun Joystick(modifier: Modifier = Modifier, enabled: Boolean, onMove: (JoystickDirection) -> Unit) {
    val bigCircleRadius = 120.dp
    val smallCircleRadius = 40.dp

    var smallCircleOffset by remember { mutableStateOf(Offset.Zero) }
    var lastDirection by remember { mutableStateOf(JoystickDirection.STOPPED) }

    val color = if (enabled) MaterialTheme.colorScheme.primary else Color.Gray

    Box(modifier = modifier.size(bigCircleRadius * 2).pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectDragGestures(
            onDragEnd = {
                smallCircleOffset = Offset.Zero
                if (lastDirection != JoystickDirection.STOPPED) {
                    onMove(JoystickDirection.STOPPED)
                    lastDirection = JoystickDirection.STOPPED
                }
            }
        ) { change, dragAmount ->
            change.consume()
            val newOffset = smallCircleOffset + dragAmount
            val radius = (bigCircleRadius - smallCircleRadius).toPx()
            val angle = atan2(newOffset.y, newOffset.x)
            val x = cos(angle) * min(radius, newOffset.getDistance())
            val y = sin(angle) * min(radius, newOffset.getDistance())
            smallCircleOffset = Offset(x, y)

            val deadZoneRatio = 0.1f
            val newDirection = if (smallCircleOffset.getDistance() < radius * deadZoneRatio) {
                lastDirection
            } else {
                val angleInDegrees = Math.toDegrees(atan2(y, x).toDouble()).toFloat()
                when (angleInDegrees) {
                    in -120f..-60f -> JoystickDirection.UP
                    in 60f..120f -> JoystickDirection.DOWN
                    in -30f..30f -> JoystickDirection.RIGHT
                    in 150f..180f, in -180f..-150f -> JoystickDirection.LEFT
                    in -60f..-30f -> JoystickDirection.UP_RIGHT
                    in -150f..-120f -> JoystickDirection.UP_LEFT
                    in 120f..150f -> JoystickDirection.DOWN_LEFT
                    in 30f..60f -> JoystickDirection.DOWN_RIGHT
                    else -> lastDirection
                }
            }

            if (newDirection != lastDirection) {
                onMove(newDirection)
                lastDirection = newDirection
            }
        }
    }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color, radius = bigCircleRadius.toPx(), style = Stroke(width = 4.dp.toPx()))
            drawCircle(color, radius = smallCircleRadius.toPx(), center = center + smallCircleOffset)
        }
    }
}

@Composable
fun DebuggerScreen(log: String, isConnected: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit, onSendData: (String) -> Unit, onClearLog: () -> Unit) {
    var textToSend by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = onConnect, enabled = !isConnected, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) { Text("Conectar") }
            Button(onClick = onDisconnect, enabled = isConnected, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Desconectar") }
        }
        TextField(value = textToSend, onValueChange = { textToSend = it }, label = { Text("Mensaje a enviar") }, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            Button(onClick = onClearLog, colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)) { Text("Limpiar") }
            Button(onClick = { if (textToSend.isNotBlank()) { onSendData(textToSend); textToSend = "" } }, enabled = isConnected) { Text("Enviar") }
        }
        Column(modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium).padding(8.dp).verticalScroll(scrollState)) {
            Text(text = log)
        }
    }
}