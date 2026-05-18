import { Component, OnInit, signal, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Toast } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { FeatureTrackingService } from './services/feature-tracking.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Toast],
  providers: [MessageService],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements OnInit {
  protected readonly title = signal('erp-front-end-web');
  private readonly featureTracking = inject(FeatureTrackingService);

  ngOnInit(): void {
    this.featureTracking.init();
  }
}
