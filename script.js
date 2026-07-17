const canvas = document.getElementById('ekranKoruyucu');
const ctx = canvas.getContext('2d');

canvas.width = window.innerWidth;
canvas.height = window.innerHeight;

let particlesArray = [];
let mouse = { x: null, y: null, radius: 100 };

// Etkileşim Dinleyicileri
window.addEventListener('mousemove', (event) => { mouse.x = event.x; mouse.y = event.y; });
window.addEventListener('touchmove', (event) => { mouse.x = event.touches[0].clientX; mouse.y = event.touches[0].clientY; });
window.addEventListener('mouseout', () => { mouse.x = undefined; mouse.y = undefined; });

class Particle {
    constructor(x, y, directionX, directionY, size, color) {
        this.x = x; this.y = y; this.directionX = directionX; this.directionY = directionY; this.size = size; this.color = color;
    }
    
    draw() {
        ctx.beginPath(); 
        ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2, false);
        ctx.fillStyle = this.color; 
        ctx.fill();
    }
    
    update() {
        if (this.x > canvas.width || this.x < 0) this.directionX = -this.directionX;
        if (this.y > canvas.height || this.y < 0) this.directionY = -this.directionY;

        let dx = mouse.x - this.x; let dy = mouse.y - this.y;
        let distance = Math.sqrt(dx * dx + dy * dy);

        if (distance < mouse.radius + this.size) {
            if (mouse.x < this.x && this.x < canvas.width - this.size * 10) this.x += 3;
            if (mouse.x > this.x && this.x > this.size * 10) this.x -= 3;
            if (mouse.y < this.y && this.y < canvas.height - this.size * 10) this.y += 3;
            if (mouse.y > this.y && this.y > this.size * 10) this.y -= 3;
        }
        this.x += this.directionX; this.y += this.directionY;
        this.draw();
    }
}

function init() {
    particlesArray = [];
    let numberOfParticles = (canvas.height * canvas.width) / 9000;
    for (let i = 0; i < numberOfParticles; i++) {
        let size = (Math.random() * 2) + 1;
        let x = (Math.random() * ((innerWidth - size * 2) - (size * 2)) + size * 2);
        let y = (Math.random() * ((innerHeight - size * 2) - (size * 2)) + size * 2);
        let directionX = (Math.random() * 2) - 1.5;
        let directionY = (Math.random() * 2) - 1.5;
        particlesArray.push(new Particle(x, y, directionX, directionY, size, '#00ffcc'));
    }
}

function connect() {
    for (let a = 0; a < particlesArray.length; a++) {
        for (let b = a; b < particlesArray.length; b++) {
            let distance = ((particlesArray[a].x - particlesArray[b].x) * (particlesArray[a].x - particlesArray[b].x)) + 
                           ((particlesArray[a].y - particlesArray[b].y) * (particlesArray[a].y - particlesArray[b].y));
            if (distance < (canvas.width / 7) * (canvas.height / 7)) {
                let opacity = 1 - (distance / 15000);
                ctx.strokeStyle = 'rgba(0, 255, 204, ' + opacity + ')';
                ctx.lineWidth = 1;
                ctx.beginPath(); ctx.moveTo(particlesArray[a].x, particlesArray[a].y); ctx.lineTo(particlesArray[b].x, particlesArray[b].y); ctx.stroke();
            }
        }
    }
}

function animate() {
    requestAnimationFrame(animate);
    ctx.clearRect(0, 0, innerWidth, innerHeight);
    for (let i = 0; i < particlesArray.length; i++) particlesArray[i].update();
    connect();
}

window.addEventListener('resize', () => { canvas.width = innerWidth; canvas.height = innerHeight; init(); });

init();
animate();
