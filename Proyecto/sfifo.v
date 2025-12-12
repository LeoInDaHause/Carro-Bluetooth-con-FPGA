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
    output reg PWM
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
// c < 434  → 434 * 40ns ≈ 17.36us
// triple → ~52us
// doble → ~104us = 9600 baudios
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
            leds <= tmp[8:1];
        end
        else begin
            i = i + 1;
            done = 0;
        end
    end 
   else done = 0;
end


 always @(posedge clk) begin
 if (leds == "A") begin  // 'A'
    IO1 <= 1;
    IO2 <= 0;
    PWM <= (cnt[18:11] < 130);

end else if (leds == "B") begin  // 'B'
    IO1 <= 0;
    IO2 <= 1;
    PWM <= (cnt[18:11] < 255);
    end
end



endmodule

