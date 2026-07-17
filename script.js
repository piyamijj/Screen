const canvas = document.getElementById('ekranKoruyucu');
const ctx = canvas.getContext('2d');

canvas.width = window.innerWidth;
canvas.height = window.innerHeight;

// Ekosistemdeki varlıkları tutacağımız listeler
let fishes = [];
let foods = [];
let eggs = [];

// Ayarlar
const MAX_FISH_SIZE = 15; // Yumurtlama boyutu
const MIN_FISH_SIZE = 3;  // Doğan balık boyutu

// Ekrana Dokunma/Tıklama (Yem Atma)
canvas.addEventListener('mousedown', dropFood);
canvas.addEventListener('touchstart', (e) => {
    // Dokunmatik ekranda varsayılan kaydırmayı engelle ve yem at
    e.preventDefault(); 
    for(let i=0; i<e.touches.length; i++) {
        dropFood({ clientX: e.touches[i].clientX, clientY: e.touches[i].clientY });
    }
}, {passive: false});

function dropFood(e) {
    // Tıklanan yere yem objesi ekle
    foods.push({
        x: e.clientX,
        y: e.clientY,
        size: 3,
        speedY: Math.random() * 0.5 + 0.5 // Yavaşça dibe batma hızı
    });
}

// 1. BALIK SINIFI (Fish)
class Fish {
    constructor(x, y, size) {
        this.x = x;
        this.y = y;
        this.size = size;
        
        // Rastgele yön ve hız
        this.angle = Math.random() * Math.PI * 2;
        this.speed = Math.random() * 1.5 + 1;
        this.color = `hsl(${Math.random() * 60 + 180}, 80%, 60%)`; // Mavi/Turkuaz tonları
        
        // Hedeflenen yem
        this.targetFood = null;
    }

    update() {
        // En yakın yemi bul (Eğer halihazırda hedefi yoksa veya hedefi başkası yediyse)
        if (foods.length > 0 && (!this.targetFood || !foods.includes(this.targetFood))) {
            let closestDist = Infinity;
            let closestFood = null;
            
            for (let food of foods) {
                let d = Math.hypot(this.x - food.x, this.y - food.y);
                if (d < closestDist) {
                    closestDist = d;
                    closestFood = food;
                }
            }
            this.targetFood = closestFood;
        } else if (foods.length === 0) {
            this.targetFood = null;
        }

        // Eğer hedef yem varsa, ona doğru dön
        if (this.targetFood) {
            let desiredAngle = Math.atan2(this.targetFood.y - this.y, this.targetFood.x - this.x);
            
            // Yumuşak dönüş (Rotasyon interpolasyonu)
            let diff = desiredAngle - this.angle;
            // Açı farkını düzelt (-PI ile PI arasında tut)
            diff = Math.atan2(Math.sin(diff), Math.cos(diff)); 
            this.angle += diff * 0.05; // Dönüş hızı
            
            // Yemi Yeme Kontrolü (Çarpışma)
            let dist = Math.hypot(this.x - this.targetFood.x, this.y - this.targetFood.y);
            if (dist < this.size + this.targetFood.size) {
                // Yemi diziden çıkar
                let index = foods.indexOf(this.targetFood);
                if (index > -1) foods.splice(index, 1);
                
                this.targetFood = null;
                this.size += 1.5; // Balık Büyür!
            }
        } else {
            // Yem yoksa rastgele ve yumuşak gezin
            this.angle += (Math.random() - 0.5) * 0.1;
        }

        // İleri Doğru Hareket
        this.x += Math.cos(this.angle) * this.speed;
        this.y += Math.sin(this.angle) * this.speed;

        // Ekran Sınırları (Dışarı çıkınca diğer taraftan girme veya dönme)
        // Burada yumuşak bir şekilde ekranın içinde kalmalarını sağlıyoruz
        if (this.x < 50 || this.x > canvas.width - 50 || this.y < 50 || this.y > canvas.height - 50) {
            let centerAngle = Math.atan2((canvas.height/2) - this.y, (canvas.width/2) - this.x);
            let diff = centerAngle - this.angle;
            diff = Math.atan2(Math.sin(diff), Math.cos(diff));
            this.angle += diff * 0.02; 
        }

        // YUMURTLAMA (Üreme Kontrolü)
        if (this.size >= MAX_FISH_SIZE) {
            // Balık platforma (ekranın en altına) doğru yüzüp yumurtlasın
            if (this.y > canvas.height - 30) {
                // Yumurtla!
                eggs.push(new Egg(this.x, canvas.height - 10));
                // Enerji harcadığı için boyutu tekrar küçülür
                this.size = MIN_FISH_SIZE + 2; 
            } else {
                 // Yumurtlamak için dibe yönel
                 let bottomAngle = Math.atan2(canvas.height - this.y, 0);
                 let diff = bottomAngle - this.angle;
                 diff = Math.atan2(Math.sin(diff), Math.cos(diff));
                 this.angle += diff * 0.08;
            }
        }
    }

    draw() {
        ctx.save();
        ctx.translate(this.x, this.y);
        ctx.rotate(this.angle);
        
        ctx.fillStyle = this.color;
        
        // Basit Balık Çizimi (Gövde ve Kuyruk)
        ctx.beginPath();
        // Gövde (Elips)
        ctx.ellipse(0, 0, this.size, this.size / 2, 0, 0, Math.PI * 2);
        ctx.fill();
        
        // Kuyruk (Üçgen)
        ctx.beginPath();
        ctx.moveTo(-this.size + 1, 0);
        ctx.lineTo(-this.size - (this.size/1.5), -this.size/1.5);
        ctx.lineTo(-this.size - (this.size/1.5), this.size/1.5);
        ctx.fill();
        
        ctx.restore();
    }
}

// 2. YUMURTA SINIFI (Egg)
class Egg {
    constructor(x, y) {
        this.x = x;
        this.y = y;
        this.size = 2;
        this.hatchTimer = 0; // Çatlama sayacı
        this.timeToHatch = Math.random() * 300 + 300; // 300 ile 600 kare arası (yaklaşık 5-10 saniye)
    }

    update() {
        this.hatchTimer++;
        // Kuluçka süresinde yavaşça büyür/titrer gibi görsel yapılabilir
        if (this.hatchTimer > this.timeToHatch) {
             // Yumurta Çatladı! Yeni balık doğar.
             fishes.push(new Fish(this.x, this.y - 10, MIN_FISH_SIZE));
             // Bu yumurtayı yok etmek için işaretle
             this.hatched = true; 
        }
    }

    draw() {
        ctx.fillStyle = '#ffcc00'; // Altın sarısı yumurta
        ctx.beginPath();
        ctx.arc(this.x, this.y, this.size, 0, Math.PI * 2);
        ctx.fill();
    }
}

// Başlangıç Kurulumu
function init() {
    fishes = [];
    foods = [];
    eggs = [];
    
    // Başlangıçta 5-10 arası rastgele balık oluştur
    let initialFishes = Math.floor(Math.random() * 5) + 5;
    for(let i=0; i<initialFishes; i++){
        fishes.push(new Fish(
            Math.random() * canvas.width, 
            Math.random() * canvas.height, 
            MIN_FISH_SIZE + Math.random() * 3
        ));
    }
}

// Ana Animasyon Döngüsü
function animate() {
    requestAnimationFrame(animate);
    
    // EKRANI TAMAMEN TEMİZLE (Kararma ve iz sorununu çözer)
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // 1. Yemleri Güncelle ve Çiz (Dibe batan yemler)
    for (let i = foods.length - 1; i >= 0; i--) {
        let f = foods[i];
        f.y += f.speedY; // Yem dibe batıyor
        
        // Çiz
        ctx.fillStyle = '#ffccaa'; // Yem rengi
        ctx.beginPath();
        ctx.arc(f.x, f.y, f.size, 0, Math.PI * 2);
        ctx.fill();

        // Yem ekranın dibine ulaştıysa yok et
        if (f.y > canvas.height) {
            foods.splice(i, 1);
        }
    }

    // 2. Yumurtaları Güncelle ve Çiz
    for (let i = eggs.length - 1; i >= 0; i--) {
        let e = eggs[i];
        e.update();
        if (e.hatched) {
            eggs.splice(i, 1); // Çatlayan yumurtayı sil
        } else {
            e.draw();
        }
    }

    // 3. Balıkları Güncelle ve Çiz
    for (let i = 0; i < fishes.length; i++) {
        fishes[i].update();
        fishes[i].draw();
    }
}

// Ekran boyutlandırma
window.addEventListener('resize', () => {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
});

// Sistemi Başlat
init();
animate();
