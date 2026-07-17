import * as THREE from 'three';

// 1. SAHNE, SİS VE KAMERA OLUŞTURMA
const scene = new THREE.Scene();
// Arka planı derin okyanus mavisi yap ve gerçekçi derinlik (sis) ekle
scene.background = new THREE.Color(0x001e3f);
scene.fog = new THREE.FogExp2(0x001e3f, 0.015); 

const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 1000);
camera.position.set(0, 0, 40); // Kamerayı biraz geriye çek

const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.setPixelRatio(window.devicePixelRatio);
document.body.appendChild(renderer.domElement);

// 2. IŞIKLANDIRMA (Atmosfer)
const ambientLight = new THREE.AmbientLight(0x004466, 2); // Genel su altı loşluğu
scene.add(ambientLight);

const directionalLight = new THREE.DirectionalLight(0xaaddff, 3); // Yukarıdan vuran güneş/ay ışığı
directionalLight.position.set(0, 50, 10);
scene.add(directionalLight);

// 3. PLANKTONLAR / SU KABARCIKLARI
const particleGeo = new THREE.BufferGeometry();
const particleCount = 800;
const posArray = new Float32Array(particleCount * 3);
for(let i = 0; i < particleCount * 3; i++) {
    posArray[i] = (Math.random() - 0.5) * 100; // Uzaya rastgele saç
}
particleGeo.setAttribute('position', new THREE.BufferAttribute(posArray, 3));
const particleMat = new THREE.PointsMaterial({ size: 0.15, color: 0xffffff, transparent: true, opacity: 0.4 });
const particlesMesh = new THREE.Points(particleGeo, particleMat);
scene.add(particlesMesh);

// 4. 3D BALIK OLUŞTURMA (Geometrik Sürü)
const fishes = [];
// Dışarıdan model yüklemediğimiz için, koni ve üçgenleri birleştirip 3D balık yapıyoruz
const bodyGeo = new THREE.ConeGeometry(0.5, 2, 8); 
bodyGeo.rotateX(Math.PI / 2); // İleriye dönük yap
const tailGeo = new THREE.ConeGeometry(0.3, 1, 3);
tailGeo.rotateX(-Math.PI / 2);
tailGeo.translate(0, 0, -1.2); // Kuyruğu arkaya al

for(let i = 0; i < 35; i++) {
    // Metalik, ışığı yansıtan gerçekçi pullar efekti
    const material = new THREE.MeshStandardMaterial({ 
        color: new THREE.Color().setHSL(Math.random() * 0.15 + 0.5, 0.8, 0.6), // Turkuaz/Mavi tonları
        roughness: 0.2, 
        metalness: 0.6 
    });
    
    const fishGroup = new THREE.Group();
    const body = new THREE.Mesh(bodyGeo, material);
    const tail = new THREE.Mesh(tailGeo, material);
    fishGroup.add(body);
    fishGroup.add(tail);
    
    // Rastgele konumlarda doğsunlar
    fishGroup.position.set((Math.random() - 0.5) * 60, (Math.random() - 0.5) * 40, (Math.random() - 0.5) * 30);
    
    // Fizik, yön ve DNA özellikleri
    fishGroup.userData = {
        velocity: new THREE.Vector3((Math.random()-0.5), (Math.random()-0.5), (Math.random()-0.5)),
        speed: Math.random() * 0.1 + 0.06,
        tailMesh: tail,
        seed: Math.random() * 100 // Her balığın yüzüş ritmi farklı olsun
    };
    
    scene.add(fishGroup);
    fishes.push(fishGroup);
}

// 5. ETKİLEŞİM VE YEM ATMA (3D Raycasting)
let food = null;
const raycaster = new THREE.Raycaster();
const mouse = new THREE.Vector2();

window.addEventListener('pointerdown', (event) => {
    // Tıklanan 2D ekran noktasını 3 boyutlu derinliğe (X,Y,Z) çevirme matematiği
    mouse.x = (event.clientX / window.innerWidth) * 2 - 1;
    mouse.y = -(event.clientY / window.innerHeight) * 2 + 1;
    raycaster.setFromCamera(mouse, camera);
    
    if(food) scene.remove(food); // Önceki yemi sil
    
    // Yeni Parlayan Yem
    const foodGeo = new THREE.SphereGeometry(0.6, 16, 16);
    const foodMat = new THREE.MeshStandardMaterial({ color: 0xffaa00, emissive: 0xff5500, emissiveIntensity: 1 });
    food = new THREE.Mesh(foodGeo, foodMat);
    
    // Yemi, kameradan tıklanan yöne doğru 25 birim ileriye at (Derinlik efekti)
    raycaster.ray.at(25, food.position); 
    scene.add(food);
});

// 6. PENCERE İLLÜZYONU (TELEFON JİROSKOPU VE FARE)
let targetCameraX = 0;
let targetCameraY = 0;

function handleOrientation(event) {
    let gamma = event.gamma; // Telefonu Sol/Sağ eğme (-90 ile 90 arası)
    let beta = event.beta;   // Telefonu İleri/Geri eğme (-180 ile 180 arası)
    
    if (gamma > 45) gamma = 45; if (gamma < -45) gamma = -45;
    if (beta > 85) beta = 85; if (beta < 5) beta = 5; // Elde tutma açısı (yaklaşık 45 derece)
    
    // Telefon eğildikçe kamerayı TERS yönde kaydırarak "Akvaryum Camı" hissi yarat
    targetCameraX = gamma * 0.3;
    targetCameraY = (beta - 45) * 0.3; 
}

// Bilgisayardan bakanlar için fareyle test etme
window.addEventListener('mousemove', (e) => {
    targetCameraX = (e.clientX / window.innerWidth - 0.5) * 20;
    targetCameraY = -(e.clientY / window.innerHeight - 0.5) * 20;
});

// 7. SİSTEMİ BAŞLATMA (Sensör izni)
document.getElementById('start-btn').addEventListener('click', async () => {
    // iOS/Android jiroskop izinleri
    if (typeof DeviceOrientationEvent !== 'undefined' && typeof DeviceOrientationEvent.requestPermission === 'function') {
        try {
            const permission = await DeviceOrientationEvent.requestPermission();
            if (permission === 'granted') window.addEventListener('deviceorientation', handleOrientation);
        } catch (error) { console.error(error); }
    } else {
        window.addEventListener('deviceorientation', handleOrientation);
    }
    
    document.getElementById('start-screen').style.opacity = '0';
    setTimeout(() => document.getElementById('start-screen').remove(), 500);
});

// 8. ANA ANİMASYON (Her saniye 60 kare çalışır)
const clock = new THREE.Clock();

function animate() {
    requestAnimationFrame(animate);
    const elapsedTime = clock.getElapsedTime();

    // Planktonların hafifçe yükselmesi ve dönmesi
    particlesMesh.position.y = Math.sin(elapsedTime * 0.2) * 5;
    particlesMesh.rotation.y = elapsedTime * 0.03;

    // Kameranın telefona/fareye göre yumuşakça kayması (Parallax Akıcılığı)
    camera.position.x += (targetCameraX - camera.position.x) * 0.05;
    camera.position.y += (targetCameraY - camera.position.y) * 0.05;
    camera.lookAt(0, 0, 0); // Kamera her zaman ekranın merkezine (su altına) baksın

    // Yem batırma fiziği
    if (food) food.position.y -= 0.06;

    // Balıkların 3 Boyutlu Yapay Zekası
    fishes.forEach(fish => {
        const data = fish.userData;
        let target = new THREE.Vector3();
        
        // Eğer yem varsa hedef orası, yoksa merkez etrafında takıl
        if (food) {
            target.copy(food.position);
        } else {
            target.set(0, 0, 0); 
        }

        const direction = new THREE.Vector3().subVectors(target, fish.position);
        const distance = direction.length();
        
        // Yemi yeme anı
        if (food && distance < 2) {
            scene.remove(food);
            food = null; 
            fish.scale.multiplyScalar(1.15); // Büyüme efekti
        }

        direction.normalize();
        
        // Yem yokken doğal ve organik bir şekilde sapmalarla yüz (Robot gibi düz gitmemesi için)
        if (!food) {
            direction.add(new THREE.Vector3(
                Math.sin(elapsedTime * 0.8 + data.seed),
                Math.cos(elapsedTime * 0.5 + data.seed),
                Math.sin(elapsedTime * 1.2 + data.seed)
            ).multiplyScalar(0.7));
        }

        // Fiziksel İvme (Lerp)
        data.velocity.lerp(direction, 0.02);
        data.velocity.normalize().multiplyScalar(data.speed);
        fish.position.add(data.velocity);

        // Balığın kafasını gittiği yöne 3 boyutlu olarak çevirmesi
        const lookTarget = fish.position.clone().add(data.velocity);
        fish.lookAt(lookTarget);

        // Kuyruk animasyonu: Yem görünce heyecanlanıp hızlanırlar
        const tailSpeed = food ? 18 : 6; 
        data.tailMesh.rotation.y = Math.sin(elapsedTime * tailSpeed + data.seed) * 0.6;
        
        // Dünyanın sınırına çarparlarsa merkeze döndür
        if (fish.position.length() > 50) {
            fish.position.normalize().multiplyScalar(49);
        }
    });

    renderer.render(scene, camera);
}

// Ekran yan çevrildiğinde (Responsive) kamerayı güncelle
window.addEventListener('resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
});

animate();
