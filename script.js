const canvas = document.getElementById('ekranKoruyucu');
const ctx = canvas.getContext('2d');
canvas.width = window.innerWidth;
canvas.height = window.innerHeight;

// Ekosistem Listeleri
let fishes = [];
let foods = [];
let eggs = [];
let bubbles = [];
let time = 0; // Animasyon zamanlayıcısı (yosunlar ve kuyruklar için)

// Ayarlar ve Türler
const ADULT_SIZE = 12; // Balığın yetişkin formuna geçeceği boyut
const MAX_SIZE = 18;   // Yumurtlama boyutu
const SPECIES = ['clownfish', 'angelfish', 'neon']; // 3 farklı tür

// --- ETKİLEŞİM (YEM ATMA) ---
canvas.addEventListener('mousedown', dropFood);
canvas.addEventListener('touchstart', (e) => {
    e.preventDefault(); 
    for(let i=0; i<e.touches.length; i++) dropFood(e.touches[i]);
}, {passive: false});

function dropFood(e) {
    foods.push({ x: e.clientX, y: e.clientY, size: 3, speedY: Math.random() * 0.5 + 0.5 });
}

// --- ARKA PLAN ELEMENTLERİ (Yosun ve Kabarcıklar) ---
function drawEnvironment() {
    // 1. Deniz Tabanı (Kum)
    ctx.fillStyle = '#2a1e12';
    ctx.beginPath();
    ctx.moveTo(0, canvas.height - 30);
    ctx.quadraticCurveTo(canvas.width / 2, canvas.height - 60, canvas.width, canvas.height - 20);
    ctx.lineTo(canvas.width, canvas.height);
    ctx.lineTo(0, canvas.height);
    ctx.fill();

    // 2. Hareketli Yosunlar (Trigonometri ile dalgalanma)
    ctx.lineWidth = 12;
    ctx.lineCap = 'round';
    for (let i = 0; i < canvas.width; i += 150) {
        ctx.strokeStyle = `hsl(140, 60%, ${20 + (i % 20)}%)`; // Farklı yeşil tonları
        let startX = i + 50;
        let startY = canvas.height;
        let height = 150 + (i % 100);
        
        // Zaman bazlı salınım
        let sway = Math.sin(time * 0.05 + i) * 30; 
        
        ctx.beginPath();
        ctx.moveTo(startX, startY);
        ctx.quadraticCurveTo(startX + sway, startY - height/2, startX + sway/2, startY - height);
        ctx.stroke();
    }

    // 3. Su Kabarcıkları
    if (Math.random() < 0.05) { // Rastgele kabarcık üret
        bubbles.push({ x: Math.random() * canvas.width, y: canvas.height, size: Math.random() * 4 + 1, speed: Math.random() * 2 + 1 });
    }
    ctx.fillStyle = 'rgba(255, 255, 255, 0.3)';
    for (let i = bubbles.length - 1; i >= 0; i--) {
        let b = bubbles[i];
        b.y -= b.speed;
        b.x += Math.sin(time * 0.1 + b.y * 0.05) * 1; // Zikzak çizerek yükselme
        ctx.beginPath(); ctx.arc(b.x, b.y, b.size, 0, Math.PI * 2); ctx.fill();
        if (b.y < 0) bubbles.splice(i, 1);
    }
}

// --- YAŞAM FORMLARI ---

class Fish {
    constructor(x, y, size, speciesCode) {
        this.x = x; this.y = y; this.size = size;
        
        // Rastgele bir tür geni seç (eğer aileden miras kalmamışsa)
        this.species = speciesCode || SPECIES[Math.floor(Math.random() * SPECIES.length)];
        this.isAdult = false;
        
        this.angle = Math.random() * Math.PI * 2;
        this.speed = Math.random() * 1.2 + 0.8;
        this.targetFood = null;
        this.tailWag = 0; // Kuyruk sallama animasyonu için
    }

    update() {
        this.tailWag += 0.2; // Kuyruk sallama hızı
        
        // Büyüme ve Evrim kontrolü
        if (this.size >= ADULT_SIZE) this.isAdult = true;

        // Yem Arama Mantığı
        if (foods.length > 0 && (!this.targetFood || !foods.includes(this.targetFood))) {
            let closestDist = Infinity, closestFood = null;
            for (let food of foods) {
                let d = Math.hypot(this.x - food.x, this.y - food.y);
                if (d < closestDist) { closestDist = d; closestFood = food; }
            }
            this.targetFood = closestFood;
        } else if (foods.length === 0) this.targetFood = null;

        // Yönelme ve Yeme
        if (this.targetFood) {
            let desiredAngle = Math.atan2(this.targetFood.y - this.y, this.targetFood.x - this.x);
            let diff = desiredAngle - this.angle;
            diff = Math.atan2(Math.sin(diff), Math.cos(diff)); 
            this.angle += diff * 0.06;
            
            if (Math.hypot(this.x - this.targetFood.x, this.y - this.targetFood.y) < this.size + 5) {
                foods.splice(foods.indexOf(this.targetFood), 1);
                this.targetFood = null;
                this.size += 2; // Yedikçe Büyür!
            }
        } else {
            this.angle += (Math.random() - 0.5) * 0.05; // Sakin yüzüş
        }

        this.x += Math.cos(this.angle) * this.speed;
        this.y += Math.sin(this.angle) * this.speed;

        // Ekran Sınırları (Yumuşak dönüş)
        if (this.x < 100 || this.x > canvas.width - 100 || this.y < 100 || this.y > canvas.height - 100) {
            let centerAngle = Math.atan2((canvas.height/2) - this.y, (canvas.width/2) - this.x);
            let diff = centerAngle - this.angle;
            diff = Math.atan2(Math.sin(diff), Math.cos(diff));
            this.angle += diff * 0.02; 
        }

        // YUMURTLAMA (Sadece yetişkin ve tok olanlar)
        if (this.size >= MAX_SIZE) {
            if (this.y > canvas.height - 60) { // Dibe yakınsa
                eggs.push(new Egg(this.x, canvas.height - 40, this.species)); // Gen aktarımı!
                this.size = ADULT_SIZE - 2; // Enerji harcadı, biraz küçüldü
            } else {
                 let bottomAngle = Math.atan2(canvas.height - this.y, 0);
                 let diff = bottomAngle - this.angle;
                 this.angle += Math.atan2(Math.sin(diff), Math.cos(diff)) * 0.05;
            }
        }
    }

    draw() {
        ctx.save();
        ctx.translate(this.x, this.y);
        ctx.rotate(this.angle);
        
        let tailSwing = Math.sin(this.tailWag) * (this.size * 0.3); // Canlı kuyruk hareketi

        if (!this.isAdult) {
            // 1. BEBEK FORMU (Yarı şeffaf, soluk renkli ortak larva görünümü)
            ctx.fillStyle = 'rgba(200, 220, 220, 0.7)';
            ctx.beginPath(); ctx.ellipse(0, 0, this.size, this.size / 2.5, 0, 0, Math.PI * 2); ctx.fill();
            // Basit bebek kuyruğu
            ctx.beginPath(); ctx.moveTo(-this.size, 0); ctx.lineTo(-this.size*1.8, tailSwing - this.size/2); ctx.lineTo(-this.size*1.8, tailSwing + this.size/2); ctx.fill();
            // Göz
            ctx.fillStyle = 'black'; ctx.beginPath(); ctx.arc(this.size/2, -this.size/6, 1, 0, Math.PI*2); ctx.fill();
        } 
        else {
            // 2. YETİŞKİN FORM (Türe göre DNA çözülür)
            if (this.species === 'clownfish') {
                // PALYAÇO BALIĞI (Turuncu/Beyaz Çizgili)
                ctx.fillStyle = '#ff6600';
                ctx.beginPath(); ctx.ellipse(0, 0, this.size, this.size / 2, 0, 0, Math.PI * 2); ctx.fill();
                // Beyaz şeritler
                ctx.strokeStyle = 'white'; ctx.lineWidth = this.size / 3;
                ctx.beginPath(); ctx.moveTo(this.size/3, -this.size/2); ctx.lineTo(this.size/3, this.size/2); ctx.stroke();
                ctx.beginPath(); ctx.moveTo(-this.size/3, -this.size/2); ctx.lineTo(-this.size/3, this.size/2); ctx.stroke();
                // Kuyruk
                ctx.fillStyle = '#ff6600'; ctx.beginPath(); ctx.moveTo(-this.size, 0); ctx.lineTo(-this.size*1.8, tailSwing - this.size/1.5); ctx.lineTo(-this.size*1.8, tailSwing + this.size/1.5); ctx.fill();
            } 
            else if (this.species === 'angelfish') {
                // MELEK BALIĞI (Görkemli üçgen gövde, uzun yüzgeçler)
                ctx.fillStyle = '#dddddd';
                ctx.beginPath(); ctx.moveTo(this.size, 0); ctx.lineTo(-this.size, -this.size*1.5); ctx.lineTo(-this.size, this.size*1.5); ctx.fill();
                // Siyah zarif çizgiler
                ctx.strokeStyle = '#222'; ctx.lineWidth = 2;
                ctx.beginPath(); ctx.moveTo(0, -this.size*0.7); ctx.lineTo(0, this.size*0.7); ctx.stroke();
                ctx.beginPath(); ctx.moveTo(-this.size/2, -this.size); ctx.lineTo(-this.size/2, this.size); ctx.stroke();
                // Kuyruk
                ctx.beginPath(); ctx.moveTo(-this.size, 0); ctx.lineTo(-this.size*1.5, tailSwing - this.size/1.5); ctx.lineTo(-this.size*1.5, tailSwing + this.size/1.5); ctx.fill();
            } 
            else if (this.species === 'neon') {
                // NEON TETRA (İnce, parlak mavi ve kırmızı)
                ctx.fillStyle = '#00ffff'; // Üst kısım Neon mavi
                ctx.beginPath(); ctx.ellipse(0, 0, this.size * 1.2, this.size / 3, 0, 0, Math.PI * 2); ctx.fill();
                ctx.strokeStyle = '#ff0044'; // Alt kırmızı çizgi
                ctx.lineWidth = this.size / 3;
                ctx.beginPath(); ctx.moveTo(-this.size, 0); ctx.lineTo(this.size, 0); ctx.stroke();
                // Saydam Kuyruk
                ctx.fillStyle = 'rgba(255,255,255,0.5)'; ctx.beginPath(); ctx.moveTo(-this.size*1.2, 0); ctx.lineTo(-this.size*2, tailSwing - this.size/2); ctx.lineTo(-this.size*2, tailSwing + this.size/2); ctx.fill();
            }
            
            // Yetişkin Gözü
            ctx.fillStyle = 'white'; ctx.beginPath(); ctx.arc(this.size/1.5, -this.size/4, this.size/5, 0, Math.PI*2); ctx.fill();
            ctx.fillStyle = 'black'; ctx.beginPath(); ctx.arc(this.size/1.5 + 1, -this.size/4, this.size/10, 0, Math.PI*2); ctx.fill();
        }
        ctx.restore();
    }
}

// YUMURTA SINIFI
class Egg {
    constructor(x, y, parentSpecies) {
        this.x = x; this.y = y; this.size = 2;
        this.species = parentSpecies; // Genetiği anneden alır!
        this.timer = 0; 
        this.timeToHatch = Math.random() * 200 + 200; 
    }
    update() {
        this.timer++;
        if (this.timer > this.timeToHatch) {
             fishes.push(new Fish(this.x, this.y - 10, 4, this.species));
             this.hatched = true; 
        }
    }
    draw() {
        // Yumurtalar türe göre hafif renkli olabilir ama şimdilik doğal altın sarısı yapalım
        ctx.fillStyle = '#ffb84d'; 
        ctx.beginPath(); ctx.arc(this.x, this.y, this.size + Math.sin(time*0.5)*0.5, 0, Math.PI * 2); ctx.fill(); // Kalp atışı gibi titrer
    }
}

// SİSTEMİ BAŞLAT
function init() {
    fishes = []; foods = []; eggs = []; bubbles = [];
    for(let i=0; i<8; i++) fishes.push(new Fish(Math.random() * canvas.width, Math.random() * canvas.height, 4, null)); // Başlangıç bebekleri
}

// ANA DÖNGÜ
function animate() {
    time += 1; 
    requestAnimationFrame(animate);
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    drawEnvironment(); // Deniz tabanı, yosunlar ve baloncuklar

    // Yemler
    for (let i = foods.length - 1; i >= 0; i--) {
        let f = foods[i]; f.y += f.speedY;
        ctx.fillStyle = '#ffccaa'; ctx.beginPath(); ctx.arc(f.x, f.y, f.size, 0, Math.PI * 2); ctx.fill();
        if (f.y > canvas.height - 20) foods.splice(i, 1); // Kuma değince kaybolur
    }

    // Yumurtalar
    for (let i = eggs.length - 1; i >= 0; i--) {
        eggs[i].update();
        if (eggs[i].hatched) eggs.splice(i, 1); else eggs[i].draw();
    }

    // Balıklar
    for (let f of fishes) { f.update(); f.draw(); }
}

window.addEventListener('resize', () => { canvas.width = window.innerWidth; canvas.height = window.innerHeight; });

init();
animate();
