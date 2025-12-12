///////////////////////////////////////////////////
//ejemplo de uso del módulo bluetooth HC06 como receptor
//adaptado a reloj de 25 MHz
///////////////////////////////////////////////////
module top(
    input clk,           //25MHz
    input reset,
    input rx,
    output reg [7:0] leds,
    output reg IO1,
    output reg IO2,
    output reg PWM,
    output reg IO3,
    output reg IO4,
    output reg PWM2
);

//fsm states
reg [1:0] presentstate, nextstate;
parameter EDO_1 = 2'b00;
parameter EDO_2 = 2'b10;

//señales
reg control=0;
reg done=0;
reg [8:0] tmp = 9'b000000000;

//contadores
reg [3:0] i = 4'b0000;
reg [8:0] c = 9'b111111111;   // <-- ajustado para 25 MHz
reg delay = 0;
reg [1:0] c2 = 2'b11;
reg capture = 0;
reg [19:0] cnt= 0;
always @(posedge clk) cnt <= cnt+1;


// ---------------------------------------------
// Retardo para reloj 25MHz
// ---------------------------------------------
always @(posedge clk)
begin
    if(c < 434)
        c = c + 1;
    else begin
        c = 0;
        delay = ~delay;
    end
end

// contador c2
always @(posedge delay)
begin
    if (c2 > 1)
        c2 = 0;
    else
        c2 = c2 + 1;
end

// captura
always @(c2)
begin
    if (c2 == 1)
        capture = 1;
    else
        capture = 0;
end

// FSM actualiza
always @(posedge capture or posedge reset)
begin
    if (reset)
        presentstate <= EDO_1;
    else
        presentstate <= nextstate;
end

// FSM lógica
always @(*)
begin
    case(presentstate)
    EDO_1:
        if(rx==1 && done==0) begin
            control = 0;
            nextstate = EDO_1;
        end 
        else if(rx==0 && done==0) begin
            control = 1;
            nextstate = EDO_2;
        end 
        else begin
            control = 0;
            nextstate = EDO_1;
        end

    EDO_2:
        if(done==0) begin
            control = 1;
            nextstate = EDO_2;
        end
        else begin
            control = 0;
            nextstate = EDO_1;
        end

    default: nextstate = EDO_1;
    endcase
end

// recepción de bits
always @(posedge capture)
begin
    if (control==1 && done==0)
        tmp <= {rx, tmp[8:1]};
end

// contador de bits
always @(posedge capture)
begin
    if (control) begin
        if(i >= 9) begin
            i = 0;
            done = 1;
            leds <= tmp[8:1];   // <-- SE GUARDA EL DATO
        end
        else begin
            i = i + 1;
            done = 0;
        end
    end 
    else done = 0;
end



// ================================
// REGISTROS PARA PWM Y MOTORES
// ================================
reg [7:0] duty  = 0;
reg [7:0] duty2 = 0;

reg IO1, IO2, IO3, IO4;
reg PWM, PWM2;

// ================================
// LÓGICA DE CONTROL SEGÚN LETRA
// ================================
always @(posedge clk or posedge reset) begin
    if (reset) begin
        IO1 <= 0;
        IO2 <= 0;
        IO3 <= 0;
        IO4 <= 0;
        duty  <= 0;
        duty2 <= 0;
    end else begin
        case (leds)

            // 'A' -> Adelante
            "A": begin
                IO1 <= 1; IO2 <= 0;  // Motor Derecho Forward
                IO3 <= 1; IO4 <= 0;  // Motor Izquierdo Forward
                duty  <= 8'd155;
                duty2 <= 8'd155;
            end

            // 'C' -> Stop/Freno
            "C": begin
                IO1 <= 0; IO2 <= 0;
                IO3 <= 0; IO4 <= 0;
                duty  <= 0;
                duty2 <= 0;
            end

            // 'B' -> Reversa
            "B": begin
                IO1 <= 0; IO2 <= 1;  // Derecho Reverse
                IO3 <= 0; IO4 <= 1;  // Izquierdo Reverse
                duty  <= 8'd155;
                duty2 <= 8'd155;
            end

            // 'F' -> Adelante Derecha (diagonal)
            "F" :begin
                IO1 <= 1; IO2 <= 0;
                IO3 <= 1; IO4 <= 0;
                duty  <= 8'd90;   // Derecho rápido
                duty2 <= 8'd155;   // Izquierdo lento
            end

            // 'D' -> Girar Derecha (pivot)
            "D": begin
                IO1 <= 0; IO2 <= 0;  // Motor derecho apagado
                IO3 <= 1; IO4 <= 0;  // Izquierdo adelante
                duty  <= 8'd90;
                duty2 <= 8'd155;
            end

            // 'E' -> Adelante Izquierda (diagonal)
            "E": begin
                IO1 <= 1; IO2 <= 0;
                IO3 <= 0; IO4 <= 0;
                duty  <= 8'd155;   // Derecho lento
                duty2 <= 8'd90;   // Izquierdo rápido
            end

            // 'G' -> Girar Izquierda (pivot)
            "G": begin
                IO1 <= 1; IO2 <= 0;  // Derecho adelante
                IO3 <= 1; IO4 <= 0;  // Izquierdo reversa
                duty  <= 8'd155;
                duty2 <= 8'd90;
            end
            "I": begin
                IO1 <= 0; IO2 <= 1;
                IO3 <= 0; IO4 <= 1;
                duty  <= 8'd90;   // Derecho lento
                duty2 <= 8'd155;   // Izquierdo rápido
            end

            // 'G' -> Girar Izquierda (pivot)
            "H": begin
                IO1 <= 0; IO2 <= 1;  // Derecho adelante
                IO3 <= 0; IO4 <= 1;  // Izquierdo reversa
                duty  <= 8'd155;
                duty2 <= 8'd90;
            end



            default: begin
                // mantener valores actuales
                IO1 <= IO1;
                IO2 <= IO2;
                IO3 <= IO3;
                IO4 <= IO4;
                duty  <= duty;
                duty2 <= duty2;
            end

        endcase
    end
end

// ================================
// GENERACIÓN DE PWM SIEMPRE ACTIVO
// ================================
always @(posedge clk)
    PWM  <= (cnt[18:11] < duty);

always @(posedge clk)
    PWM2 <= (cnt[18:11] < duty2);

endmodule
