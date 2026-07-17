const canvas = document.getElementById('ekranKoruyucu');
const ctx = canvas.getContext('2d');

canvas.width = window.innerWidth;
canvas.height = window.innerHeight;

let particles = [];
// Fare başlangıçta ekran dışında kalsın ki merkezde toplanma yapmasın
const mouse = { x: -1000, y: -1000 };

// Yumuşak etkileşim için dinleyiciler
window.addEventListener('mousemove', (e) => { mouse.x = e.clientX; mouse.y = e.clientY; });
window.addEventListener('touchmove', (e) => { mouse.x = e.touches[0].clientX; mouse.y = e.touches[0].clientY; });
window.addEventListener('mouseout', () => { mouse.x = -1000; mouse.y = -1000; });

class Particle {
    constructor() {
        this.x = Math.random() * canvas.width;
        this.y = Math.random() * canvas.height;
        this.size = Math.random() * 1.5 + 0.5; // Daha ince ve zarif noktalar
        this.speed = Math.random() * 0.8 + 0.2; // Yavaş ve dinlendirici hız
        this.angle = Math.random() * Math.PI * 2; // Organik dalga yönü
        
        // HSL renk paleti: 180 ile 240 arası (Turkuaz, Cyan, Derin Mavi ve Mor tonları)
        this.baseHue = Math.random() * 60 + 180; 
        this.hue = this.baseHue;
    }

    update() {
        // Sinüs ve Kosinüs ile organik "akışkan" hareket
        this.angle += 0.02;
        this.x += Math.cos(this.angle) * this.speed;
        this.y += Math.sin(this.angle) * this.speed;

        // Fare ile olan mesafeyi hesapla
        let dx = mouse.x - this.x;
        let dy = mouse.y - this.y;
        let distance = Math.sqrt(dx * dx + dy * dy);
        
        // Fareye 250 piksel yaklaşıldığında yumuşak bir dalgalanma yarat
        if (distance < 250) {
            let force = (250 - distance) / 250;
            // Parçacıkları farenin etrafından nazikçe kaydır
            this.x -= (dx / distance) * force * 1.5;
            this.y -= (dy / distance) * force * 1.5;
            
            // Etkileşime girince renkleri geçici olarak ısıt (Mordan pembeye/turkuaza kayış)
            this.hue += 2; 
        } else {
            // Fare uzaklaşınca orijinal rahatlatıcı rengine yavaşça dön
            if (this.hue > this.baseHue) this.hue -= 0.5;
        }

        // Parçacıklar ekran dışına çıkarsa diğer taraftan geri dönsün (Sonsuz döngü)
        if (this.x < 0) this.x = canvas.width;
        if (this.x > canvas.width) this.x = 0;
        if (this.y < 0) this.y = canvas.height;
        if (this.y > canvas.height) this.y = 0;
    }

    draw() {
        // Parlaklık ve doygunluk ayarı ile neon etkisi
        ctx.fillStyle = `hsl(${this.hue}, 80%, 60%)`;
        ctx.beginPath();
        ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
        ctx.fill();
    }
}

function init() {
    particles = [];
    // Cihazın ekran çözünürlüğüne göre yoğunluğu ayarla (Öncekinden daha yoğun ama daha performanslı)
    const particleCount = (canvas.width * canvas.height) / 3000; 
    for (let i = 0; i < particleCount; i++) {
        particles.push(new Particle());
    }
}

function animate() {
    // SİHİRLİ DOKUNUŞ: Ekranı tamamen silmek yerine yarı şeffaf siyahla boyuyoruz.
    // Bu, ışıkların arkasında muazzam bir "hareket izi" (motion blur) bırakmasını sağlar.
    ctx.fillStyle = 'rgba(5, 5, 8, 0.06)'; 
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    
    // Tüm parçacıkları güncelle ve çiz
    particles.forEach(p => {
        p.update();
        p.draw();
    });
    
    requestAnimationFrame(animate);
}

// Ekran boyutu değiştiğinde sorunsuz tepki ver
window.addEventListener('resize', () => {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
    init();
});

// Sistemi Başlat
init();
animate();
